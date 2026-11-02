package com.bptn.feedApp.service;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.bptn.feedApp.exception.domain.FeedNotFoundException;
import com.bptn.feedApp.exception.domain.UserNotFoundException;
import com.bptn.feedApp.jpa.Feed;
import com.bptn.feedApp.jpa.User;
import com.bptn.feedApp.repository.FeedRepository;
import com.bptn.feedApp.repository.UserRepository;

@Service
public class FeedService {
	
	@Autowired
	UserRepository userRepository;

	@Autowired
	FeedRepository feedRepository;

	final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public Feed createFeed(Feed feed) {

	    String username = SecurityContextHolder.getContext().getAuthentication().getName();

	    User user = this.userRepository.findByUsername(username)
	                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

	    feed.setUser(user);
	    feed.setCreatedOn(Timestamp.from(Instant.now()));

	    return this.feedRepository.save(feed);
	}
	
	public Feed getFeedById(int feedId) {

	    return this.feedRepository.findById(feedId)
	    .orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));
	}
}
