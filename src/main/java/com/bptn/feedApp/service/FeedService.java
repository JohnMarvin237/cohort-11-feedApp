package com.bptn.feedApp.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.bptn.feedApp.domain.PageResponse;
import com.bptn.feedApp.exception.ContentModerationException;
import com.bptn.feedApp.exception.domain.FeedNotFoundException;
import com.bptn.feedApp.exception.domain.FeedNotUserException;
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

	@Autowired
	ModerationModel moderationModel;

	final Logger logger = LoggerFactory.getLogger(this.getClass());

	public Feed createFeed(Feed feed) throws ContentModerationException {

		String username = SecurityContextHolder.getContext().getAuthentication().getName();

		User user = this.userRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
		
		if (feed.getContent() == null || feed.getContent().isBlank()) {
			throw new IllegalArgumentException("Feed content cannot be empty");
		}

		// AI Gatekeeper: check the text before it reaches the database
		Moderation moderation = this.moderationModel.call(new ModerationPrompt(feed.getContent())).getResult().getOutput();

		for (ModerationResult result : moderation.getResults()) {
			if (result.isFlagged()) {
				List<String> categories = this.getFlaggedCategories(result.getCategories());
				this.logger.debug("Feed rejected by moderation, username: {}, categories: {}", username, categories);
				throw new ContentModerationException(categories);
			}
		}

		feed.setUser(user);
		feed.setCreatedOn(Timestamp.from(Instant.now()));

		return this.feedRepository.save(feed);
	}

	private List<String> getFlaggedCategories(Categories c) {

		List<String> flagged = new ArrayList<>();

		if (c.isHate())
			flagged.add("Hate Speech");
		if (c.isHateThreatening())
			flagged.add("Hate/Threatening");
		if (c.isHarassment())
			flagged.add("Harassment");
		if (c.isHarassmentThreatening())
			flagged.add("Harassment/Threatening");
		if (c.isViolence())
			flagged.add("Violence");
		if (c.isViolenceGraphic())
			flagged.add("Graphic Violence");
		if (c.isSelfHarm())
			flagged.add("Self-Harm");
		if (c.isSexual())
			flagged.add("Sexual");
		if (c.isSexualMinors())
			flagged.add("Sexual/Minors");

		return flagged;
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
				.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

		Page<Feed> paged = this.feedRepository.findByUserNot(user,
				PageRequest.of(pageNum, pageSize, Sort.by("feedId").descending()));

		return new PageResponse<Feed>(paged);
	}

	public FeedMetaData createFeedMetaData(int feedId, FeedMetaData meta) {

		String username = SecurityContextHolder.getContext().getAuthentication().getName();

		User user = this.userRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

		Feed feed = this.feedRepository.findById(feedId)
				.orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));

		FeedMetaData newMeta = new FeedMetaData();

		newMeta.setIsLike(false);
		newMeta.setUser(user);
		newMeta.setFeed(feed);
		newMeta.setCreatedOn(Timestamp.from(Instant.now()));

		if (Optional.ofNullable(meta.getIsLike()).isPresent()) {

			newMeta.setIsLike(meta.getIsLike());

			if (meta.getIsLike()) {

				feed.getFeedMetaData().stream().filter(m -> m.getUser().getUsername().equals(username))
						.filter(m -> m.getIsLike().equals(true)).findAny().ifPresent(m -> {
							throw new LikeExistException(
									String.format("Feed already liked, feedId: %d, username: %s", feedId, username));
						});

				newMeta.setComment("");
			}
		}

		if (!newMeta.getIsLike()) {
			newMeta.setComment(meta.getComment());
		}

		return this.feedMetaDataRepository.save(newMeta);
	}

	public void deleteFeed(int feedId) {

		String username = SecurityContextHolder.getContext().getAuthentication().getName();

		Feed feed = this.feedRepository.findById(feedId)
				.orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));

		Optional.of(feed).filter(f -> f.getUser().getUsername().equals(username))
				.orElseThrow(() -> new FeedNotUserException(String
						.format("Feed doesn't belong to current User, feedId: %d, username: %s", feedId, username)));

		this.feedRepository.delete(feed);
	}
}
