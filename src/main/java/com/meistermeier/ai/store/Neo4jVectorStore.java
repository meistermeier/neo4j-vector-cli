package com.meistermeier.ai.store;

import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.Functions;
import org.neo4j.cypherdsl.core.Property;
import org.neo4j.cypherdsl.core.internal.SchemaNames;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Gerrit Meier
 */
public class Neo4jVectorStore {

		// Those are not yet configurable
		private static final Function<String, String> INDEX_NAME_FUNCTION = "neo4j-vector-index-%s"::formatted;
		private static final String DISTANCE_TYPE = "cosine";
		private static final Integer EMBEDDING_DIMENSION = 1536;

		private static final Renderer CYPHER_RENDERER = Renderer.getRenderer(Configuration.newConfig().withDialect(Dialect.NEO4J_5).build());
		private static final String ELEMENT_ID_FIELD_NAME = "__ELEMENT_ID__";
		private static final String NODE_NAME = "_N_";
		private static final String PROPERTY_MAP_NAME = "_PROPERTIES_";

		private final Driver driver;
		private final OpenAiConnection openAiConnection;
		private final Neo4jVectorStoreConfig config;

		public record Neo4jVectorStoreConfig(String label, String embeddingProperty, String model, boolean verbose) {
				public String quotedLabel() {
						return SchemaNames.sanitize(this.label).orElseThrow();
				}
		}

		public Neo4jVectorStore(Driver driver, OpenAiConnection openAiConnection, Neo4jVectorStoreConfig config) {
				this.driver = driver;
				this.openAiConnection = openAiConnection;
				this.config = config;
		}

		public void createEmbedding(List<String> properties) {
				List<NodePropertiesWrapper> nodesToProcess;

				org.neo4j.cypherdsl.core.Node cypherNode = Cypher.node(this.config.label).named(NODE_NAME);
				List<Property> propertyList = new ArrayList<>(properties.stream().map(cypherNode::property).toList());
				Expression elementId = Functions.elementId(cypherNode).as(ELEMENT_ID_FIELD_NAME);
				Expression project = cypherNode.project((Object[]) propertyList.toArray(new Property[]{})).as(PROPERTY_MAP_NAME);
				var statement = Cypher.match(cypherNode).returning(project, elementId).build();

				nodesToProcess = new ArrayList<>(driver.executableQuery(CYPHER_RENDERER.render(statement)).execute().records().stream()
						.map(record -> new NodePropertiesWrapper(record.get(ELEMENT_ID_FIELD_NAME).asString(), record.get(PROPERTY_MAP_NAME).asMap())).toList());

				if (config.verbose()) {
						System.out.printf("Found %d nodes to process.%n", nodesToProcess.size());
				}
				var nodeEmbeddings = nodesToProcess.stream()
						.filter(nodePropertiesWrapper -> !reduceToSingleString(nodePropertiesWrapper.properties).isEmpty())
						.map(nodePropertiesWrapper -> {
								try {
										var embeddingResponse = this.openAiConnection.createEmbedding(reduceToSingleString(nodePropertiesWrapper.properties), config.model());
										return Map.of("elementId", nodePropertiesWrapper.elementId, "embedding", embeddingResponse);
								} catch (EmbeddingCreationException e) {
										// is this nice? No
										return Map.of();
								}
						}).toList();

				// remove empty entries
				nodeEmbeddings = nodeEmbeddings.stream().filter(map -> !map.containsValue(Map.of())).toList();

				statement = Cypher.unwind(Cypher.parameter("rows")).as("row")
						.match(cypherNode)
						.where(Functions.elementId(cypherNode).eq(Cypher.property("row", "elementId")))
						.with(Cypher.name("row"), cypherNode)
						.call("db.create.setNodeVectorProperty")
						.withArgs(cypherNode.getRequiredSymbolicName(), Cypher.parameter("embeddingProperty"), Cypher.property("row", "embedding"))
						.withoutResults()
						.returning(Functions.count(cypherNode))
						.build();

				driver.executableQuery(CYPHER_RENDERER.render(statement)).withParameters(Map.of("rows", nodeEmbeddings, "embeddingProperty", config.embeddingProperty)).execute();
		}

		private String reduceToSingleString(Map<String, Object> properties) {
				return properties.values()
						.stream()
						.filter(Objects::nonNull)
						.map(Object::toString)
						.collect(Collectors.joining("\\n"));
		}

		public Map<Node, Float> similaritySearch(String query, int numberOfNearestNeighbours, double threshold) throws EmbeddingCreationException {
				var embedding = Values.value(toFloatArray(this.openAiConnection.createEmbedding(query, config.model())));
				return driver.executableQuery("""
								CALL db.index.vector.queryNodes($indexName, $numberOfNearestNeighbours, $embeddingValue)
								YIELD node, score
								WHERE score >= $threshold
								RETURN node, score
								""")
						.withParameters(
								Map.of(
										"indexName", INDEX_NAME_FUNCTION.apply(this.config.label),
										"numberOfNearestNeighbours", numberOfNearestNeighbours,
										"embeddingValue", embedding,
										"threshold", threshold
								)
						)
						.execute().records().stream()
						.map(Neo4jVectorStore::recordToEntry)
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}

		public void createIndexIfNotExists() {

				driver.executableQuery("CREATE CONSTRAINT %s_unique_idx IF NOT EXISTS FOR (n:%s) REQUIRE n.id IS UNIQUE".formatted(
								SchemaNames.sanitize(this.config.label + "_unique_idx").orElseThrow(), this.config.quotedLabel()))
						.execute();

				if (!vectorIndexExists()) {
						String indexName = INDEX_NAME_FUNCTION.apply(config.label());
						if (config.verbose()) {
								System.out.println("Creating vector index " + indexName);
						}
						var statement = "CALL db.index.vector.createNodeIndex($indexName, $label, $embeddingProperty, $embeddingDimension, $distanceType)";
						driver.executableQuery(statement).withParameters(
										Map.of("indexName", indexName, "label", this.config.label(), "embeddingProperty",
												config.embeddingProperty, "embeddingDimension", EMBEDDING_DIMENSION, "distanceType", DISTANCE_TYPE))
								.execute();
						driver.executableQuery("CALL db.awaitIndexes()").execute();
						if (config.verbose()) {
								System.out.println("Created vector index " + indexName);
						}
				}
		}

		public boolean vectorIndexExists() {
				return driver.executableQuery("SHOW INDEXES YIELD name WHERE name = $name RETURN count(*) > 0").withParameters(Map.of("name", INDEX_NAME_FUNCTION.apply(this.config.label)))
						.execute().records()
						.get(0).get(0)
						.asBoolean();
		}

		private static float[] toFloatArray(List<Double> embeddingDouble) {
				float[] embeddingFloat = new float[embeddingDouble.size()];
				int i = 0;
				for (Double d : embeddingDouble) {
						embeddingFloat[i++] = d.floatValue();
				}
				return embeddingFloat;
		}

		private static Map.Entry<Node, Float> recordToEntry(org.neo4j.driver.Record neoRecord) {
				var node = neoRecord.get("node").asNode();
				var score = neoRecord.get("score").asFloat();
				return Map.entry(node, score);
		}

		private record NodePropertiesWrapper(String elementId, Map<String, Object> properties) {
		}

}
