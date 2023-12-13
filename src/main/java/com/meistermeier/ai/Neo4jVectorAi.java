package com.meistermeier.ai;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "neo4j-vector-cli", subcommands = {SearchCommand.class, EmbeddingCommand.class}, defaultValueProvider = CommandLine.PropertiesDefaultProvider.class, resourceBundle = "neo4j-vector-cli")
public class Neo4jVectorAi {

		@CommandLine.Option(names = "--model", required = true, defaultValue = "text-embedding-ada-002", descriptionKey = "model")
		String model;

		@CommandLine.Option(names = "--uri", required = true, defaultValue = "bolt://localhost:7687", descriptionKey = "uri")
		String uri;

		@CommandLine.Option(names = "--user", required = true, defaultValue = "neo4j", descriptionKey = "user")
		String user;

		@CommandLine.Option(names = "--password", required = false, descriptionKey = "password")
		String password;

		@CommandLine.Option(names = {"--verbose", "-v"}, required = false, descriptionKey = "verbose")
		boolean verbose;

		@CommandLine.Option(names = "--label", required = true, descriptionKey = "label")
		String label;

		@CommandLine.Option(names = "--embedding-property", required = true, defaultValue = "embedding", descriptionKey = "embedding-property")
		String embeddingProperty;

		public static void main(String[] args) {
				System.exit(new CommandLine(Neo4jVectorAi.class)
						.execute(args));
		}

}

