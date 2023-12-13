package com.meistermeier.ai;

import com.meistermeier.ai.store.EmbeddingCreationException;
import com.meistermeier.ai.store.Neo4jVectorStore;
import com.meistermeier.ai.store.OpenAiConnection;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.types.Node;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author Gerrit Meier
 */
@CommandLine.Command(name = "search", description = "Search with any phrase in the embeddings.")
public class SearchCommand implements Callable<Integer> {

		@CommandLine.ParentCommand
		Neo4jVectorAi parentCommand;

		@CommandLine.Parameters(descriptionKey = "phrase")
		String phrase;

		@CommandLine.Option(required = true, names = {"--limit", "-l"}, defaultValue = "5", descriptionKey = "limit")
		Integer limit;

		@CommandLine.Option(required = true, names = {"--threshold", "-t"}, defaultValue = "0.0", descriptionKey = "threshold")
		Double threshold;

		@CommandLine.Option(required = true, names = {"--format", "-f"}, defaultValue = "parameter", descriptionKey = "format")
		String format;

		@CommandLine.Option(required = true, names = {"--properties", "-p"}, defaultValue = "", split = ",", paramLabel = "property", descriptionKey = "properties")
		List<String> properties;

		@Override
		public Integer call() {
				try {
						return search(phrase, limit, threshold);
				} catch (EmbeddingCreationException e) {
						System.err.println(e.getMessage());
						if (parentCommand.verbose) {
								e.printStackTrace(System.err);
						}
						return 1;
				}
		}

		private int search(String phrase, int n, double threshold) throws EmbeddingCreationException {
				String openAiToken = Optional.ofNullable(System.getenv("OPENAI_API_KEY")).orElseThrow(() -> new IllegalStateException("Missing env OPENAI_API_KEY"));
				Driver driver = GraphDatabase.driver(parentCommand.uri, AuthTokens.basic(parentCommand.user, parentCommand.password));
				OpenAiConnection openAiConnection = new OpenAiConnection(openAiToken);
				Neo4jVectorStore neo4jVectorStore = new Neo4jVectorStore(driver, openAiConnection, new Neo4jVectorStore.Neo4jVectorStoreConfig(parentCommand.label, parentCommand.embeddingProperty, parentCommand.model, parentCommand.verbose));
				if (!neo4jVectorStore.vectorIndexExists()) {
						System.err.println("Vector index does not exist.");
						return 2;
				}
				var searchResult = sortBySimilarity(neo4jVectorStore.similaritySearch(phrase, n, threshold));
				switch (format) {
						case "parameter" -> renderParameter(searchResult, this.properties);
						case "console" -> renderConsole(searchResult, this.properties);
				}

				return 0;
		}

		private static void renderParameter(Map<Node, Float> searchResult, List<String> properties) {
				System.out.format("{records:[%s]}", renderRecords(searchResult, properties));
		}

		private static void renderConsole(Map<Node, Float> searchResult, List<String> properties) {
				System.out.format("%-30s%-120s%-12s%n", "Labels", "Properties", "Similarity");
				searchResult.forEach((key, value) -> {
						String actualPropertyString = key.asMap().entrySet().stream().filter(entry -> properties.contains(entry.getKey())).toList().toString();
						System.out.format("%-30s%-120s%-12s%n",
								key.labels(),
								actualPropertyString.length() > 119 ? actualPropertyString.substring(0, 116) + "..." : actualPropertyString,
								value);
				});

		}

		private static String renderRecords(Map<Node, Float> searchResult, List<String> properties) {
				return searchResult.entrySet().stream()
						.map(entry -> "{" +
								"__elementId__:\"" + entry.getKey().elementId() + "\"," +
								printProperties(properties, entry) + (!printProperties(properties, entry).isEmpty() ? "," : "") +
								"__similarity__:" + entry.getValue()
								+ "}")
						.collect(Collectors.joining(","));
		}

		private static Map<Node, Float> sortBySimilarity(Map<Node, Float> map) {
				List<Map.Entry<Node, Float>> sortList = new ArrayList<>(map.entrySet());
				sortList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

				return sortList.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
		}

		private static String printProperties(List<String> properties, Map.Entry<Node, Float> entry) {
				return entry.getKey().asMap().entrySet().stream().filter(property -> properties.contains(property.getKey()))
						.map(property -> {
								var key = property.getKey();
								var value = property.getValue();
								return key + ":" + ((value instanceof String) ? "\"" + value + "\"" : value);
						}).collect(Collectors.joining(","));
		}

}
