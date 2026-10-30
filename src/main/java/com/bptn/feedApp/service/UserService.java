package com.bptn.feedApp.service;

import com.bptn.feedApp.repository.UserRepository;
import com.bptn.feedApp.jpa.User;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import com.bptn.feedApp.exception.domain.EmailExistException;
import com.bptn.feedApp.exception.domain.UsernameExistException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bptn.feedApp.exception.domain.UserNotFoundException;
import com.bptn.feedApp.exception.domain.EmailNotVerifiedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.bptn.feedApp.provider.ResourceProvider;
import com.bptn.feedApp.security.JwtService;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

	final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	UserRepository userRepository;

	@Autowired
	EmailService emailService;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	JwtService jwtService;

	@Autowired
	ResourceProvider provider;

	public List<User> listUsers() {
		return this.userRepository.findAll();
	}

	public Optional<User> findByUsername(String username) {
		return this.userRepository.findByUsername(username);
	}

//	public void createUser(User user) {
//		this.userRepository.save(user);
//	}

	public User signup(User user) {
		user.setUsername(user.getUsername().toLowerCase());
		user.setEmailId(user.getEmailId().toLowerCase());

		this.validateUsernameAndEmail(user.getUsername(), user.getEmailId());

		user.setEmailVerified(false);
		user.setPassword(this.passwordEncoder.encode(user.getPassword()));
		user.setCreatedOn(Timestamp.from(Instant.now()));

		this.userRepository.save(user);

		this.emailService.sendVerificationEmail(user);

		return user;
	}

	private void validateUsernameAndEmail(String username, String emailId) {

		this.userRepository.findByUsername(username).ifPresent(u -> {
			throw new UsernameExistException(String.format("Username already exists, %s", u.getUsername()));
		});

		this.userRepository.findByEmailId(emailId).ifPresent(u -> {
			throw new EmailExistException(String.format("Email already exists, %s", u.getEmailId()));
		});

	}

	public void verifyEmail() {

		String username = SecurityContextHolder.getContext().getAuthentication().getName();

		User user = this.userRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

		user.setEmailVerified(true);

		this.userRepository.save(user);
	}

	private static User isEmailVerified(User user) {

		if (user.getEmailVerified().equals(false)) {
			throw new EmailNotVerifiedException(String.format("Email requires verification, %s", user.getEmailId()));
		}

		return user;
	}

	private Authentication authenticate(String username, String password) {
		return this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
	}

	public User authenticate(User user) {

		/* Spring Security Authentication. */
		this.authenticate(user.getUsername(), user.getPassword());

		/* Get User from the DB. */
		return this.userRepository.findByUsername(user.getUsername()).map(UserService::isEmailVerified).get();
	}

	public HttpHeaders generateJwtHeader(String username) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(AUTHORIZATION, this.jwtService.generateJwtToken(username, this.provider.getJwtExpiration()));

		return headers;
	}
	
	public void sendResetPasswordEmail(String emailId) {

		  Optional<User> opt = this.userRepository.findByEmailId(emailId);

		  if (opt.isPresent()) {
		    this.emailService.sendResetPasswordEmail(opt.get());
		  } else {
		    logger.debug("Email doesn't exist, {}", emailId);
		  }
		}
	
	public void resetPassword(String password) {

	    String username = SecurityContextHolder.getContext().getAuthentication().getName();

	    User user = this.userRepository.findByUsername(username)
	                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s",username)));

	    user.setPassword(this.passwordEncoder.encode(password));

	    this.userRepository.save(user);
	}
	
	public User getUser() {

	    String username = SecurityContextHolder.getContext().getAuthentication().getName();

	    /* Get User from the DB. */
	    return this.userRepository.findByUsername(username)
	.orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s",username)));
	}
}
