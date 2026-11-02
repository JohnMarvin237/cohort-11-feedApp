package com.bptn.feedApp.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.bptn.feedApp.domain.PageResponse;
import com.bptn.feedApp.exception.domain.FeedNotFoundException;
import com.bptn.feedApp.exception.domain.LikeExistException;
import com.bptn.feedApp.exception.domain.UserNotFoundException;
import com.bptn.feedApp.jpa.Feed;
import com.bptn.feedApp.jpa.FeedMetaData;
import com.bptn.feedApp.jpa.User;
import com.bptn.feedApp.repository.FeedMetaDataRepository;
import com.bptn.feedApp.repository.FeedRepository;
import com.bptn.feedApp.repository.UserRepository;

@Service
public class FeedService {

	@Autowired
	UserRepository userRepository;

	@Autowired
	FeedRepository feedRepository;
	
	@Autowired
	FeedMetaDataRepository feedMetaDataRepository;

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

	public PageResponse<Feed> getUserFeeds(int pageNum, int pageSize) {

		String username = SecurityContextHolder.getContext().getAuthentication().getName();

		User user = this.userRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

		Page<Feed> paged = this.feedRepository.findByUser(user,
				PageRequest.of(pageNum, pageSize, Sort.by("feedId").descending()));

		return new PageResponse<Feed>(paged);
	}
	
	public PageResponse<Feed> getOtherUsersFeeds(int pageNum, int pageSize) {

	    String username = SecurityContextHolder.getContext().getAuthentication().getName();     
	    User user = this.userRepository.findByUsername(username)
	                 .orElseThrow(()-> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
	                
	    Page<Feed> paged = this.feedRepository.findByUserNot(user, PageRequest.of(pageNum, pageSize, Sort.by("feedId").descending()));
	        
	    return new PageResponse<Feed>(paged);
	}
	
	public FeedMetaData createFeedMetaData(int feedId, FeedMetaData meta) {
        
	    String username = SecurityContextHolder.getContext().getAuthentication().getName();
	        
	    User user = this.userRepository.findByUsername(username)
	                             .orElseThrow(()-> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
	        
	    Feed feed = this.feedRepository.findById(feedId)
	                             .orElseThrow(()-> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));

	    FeedMetaData newMeta = new FeedMetaData();
	        
	    newMeta.setIsLike(false);
	    newMeta.setUser(user);
	    newMeta.setFeed(feed);
	    newMeta.setCreatedOn(Timestamp.from(Instant.now()));
	        
	    if (Optional.ofNullable(meta.getIsLike()).isPresent()) {
	            
	        newMeta.setIsLike( meta.getIsLike() );
	            
	        if (meta.getIsLike()) {
	                
	            feed.getFeedMetaData().stream()
	                      .filter(m -> m.getUser().getUsername().equals(username))
	                      .filter(m -> m.getIsLike().equals(true)).findAny()
	                      .ifPresent(m -> {throw new LikeExistException(String.format("Feed already liked, feedId: %d, username: %s", feedId, username));});
	                
	            newMeta.setComment("");
	        }
	    } 
	        
	    if (!newMeta.getIsLike()) {
	        newMeta.setComment(meta.getComment());
	    }
	        
	    return this.feedMetaDataRepository.save(newMeta);
	}
}
