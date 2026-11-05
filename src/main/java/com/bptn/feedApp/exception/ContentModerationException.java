package com.bptn.feedApp.exception;

import java.util.List;

public class ContentModerationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	private final List<String> categories;
	
	public ContentModerationException(List<String> categories) {
		super("Post rejected for violating community standards: " + String.join(", ", categories));
		this.categories = categories;
	}

	public List<String> getCategories() {
		return categories;
	}
}
