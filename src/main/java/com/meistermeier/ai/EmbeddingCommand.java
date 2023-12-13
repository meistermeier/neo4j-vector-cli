package com.meistermeier.ai;

import com.meistermeier.ai.store.Neo4jVectorStore;
import com.meistermeier.ai.store.OpenAiConnection;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import picocli.CommandLine;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * @author Gerrit Meier
 */
@CommandLine.Command(name = "create-embedding", description = "Create embedding", defaultValueProvider = CommandLine.PropertiesDefaultProvider.class)
public class EmbeddingCommand implements Callable<Integer> {

		@CommandLine.ParentCommand
		Neo4jVectorAi parentCommand;

		@CommandLine.Option(names = {"--properties", "-p"}, descriptionKey = "properties", paramLabel = "property", required = true, split = ",")
		List<String> properties;

		@Override
		public Integer call() {
				createEmbedding();
				return 0;
		}

		/**
		 * create embedding directly with the embeddingClient.
		 * Using the vector store would cause duplicate data because of the "document-based" approach.
		 */
		private void createEmbedding() {
				String openAiToken = Optional.ofNullable(System.getenv("OPENAI_API_KEY")).orElseThrow(() -> new IllegalStateException("Missing env OPENAI_API_KEY"));
				Driver driver = GraphDatabase.driver(parentCommand.uri, AuthTokens.basic(parentCommand.user, parentCommand.password));
				OpenAiConnection openAiConnection = new OpenAiConnection(openAiToken);
				Neo4jVectorStore neo4jVectorStore = new Neo4jVectorStore(driver, openAiConnection, new Neo4jVectorStore.Neo4jVectorStoreConfig(parentCommand.label, parentCommand.embeddingProperty, parentCommand.model, parentCommand.verbose));
				neo4jVectorStore.createIndexIfNotExists();
				neo4jVectorStore.createEmbedding(properties);
		}
}
