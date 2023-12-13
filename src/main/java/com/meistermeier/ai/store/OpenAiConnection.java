package com.meistermeier.ai.store;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

/**
 * @author Gerrit Meier
 */
public class OpenAiConnection {

		private static final String OPEN_AI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

		private final String openaiApiKey;

		public OpenAiConnection(String openaiApiKey) {
				this.openaiApiKey = openaiApiKey;
		}

		public List<Double> createEmbedding(String input, String model) throws EmbeddingCreationException {
				try {
						String requestPayload = "{\"input\": \"%s\", \"model\": \"%s\"}".formatted(input, model);
						var request = HttpRequest.newBuilder()
								.uri(new URI(OPEN_AI_EMBEDDING_URL))
								.headers(
										"Content-Type", "application/json",
										"Authorization", "Bearer %s".formatted(this.openaiApiKey))
								.POST(HttpRequest.BodyPublishers.ofString(requestPayload))
								.build();

						HttpClient client = HttpClient.newHttpClient();
						HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
						var json = response.body();
						if (response.statusCode() != 200) {
								var exceptionMessage = """
										Could not load ate embedding. Got response:
										%s
										for request:
										%s""".formatted(json, requestPayload);
								throw new EmbeddingCreationException(exceptionMessage, new IllegalStateException());
						}

						// regexes are for people who know what they are doing
						String str = "embedding\": [";
						int startIndex = json.indexOf(str);

						int endIndex = json.indexOf("]", startIndex);

						var substring = json.substring(startIndex + str.length(), endIndex);

						var dings = substring.replaceAll("\\s+", "");
						return Arrays.stream(dings.split(",")).map(Double::parseDouble).toList();

				} catch (URISyntaxException | InterruptedException | IOException e) {
						throw new EmbeddingCreationException("Could not create embedding", e);
				}
		}

}
