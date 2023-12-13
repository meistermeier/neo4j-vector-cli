package com.meistermeier.ai.store;

/**
 * @author Gerrit Meier
 */
public class EmbeddingCreationException extends Exception {
		public EmbeddingCreationException(String message, Exception e) {
				super(message, e);
		}
}
