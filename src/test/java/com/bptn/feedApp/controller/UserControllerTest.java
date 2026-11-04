package com.bptn.feedApp.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.bptn.feedApp.jpa.User;
import com.bptn.feedApp.repository.UserRepository;
import com.bptn.feedApp.security.JwtService;
import com.bptn.feedApp.service.EmailService;

// Jackson 3 (Spring Boot 4): the packages moved from com.fasterxml.jackson to tools.jackson
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class UserControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	/*
	 * @MockBean was removed in Spring Boot 4: use @MockitoBean. Avoids sending a
	 * real email on signup.
	 */
	@MockitoBean
	EmailService emailService;

	/*
	 * Built once per test in setup(). USE_ANNOTATIONS is disabled so the password
	 * (typically write-only on the entity) is included when serializing the user.
	 */
	ObjectMapper objectMapper;

	User user;
	String otherUsername;
	String otherPassword;

	@BeforeEach
	public void setup() {

		this.objectMapper = JsonMapper.builder().disable(MapperFeature.USE_ANNOTATIONS).build();

		this.user = new User();

		this.user.setFirstName("John");
		this.user.setLastName("Doe");
		this.user.setUsername("johndoe");
		this.user.setPassword("mypassword");
		this.user.setPhone("987654321");
		this.user.setEmailId("johndoe@example.com");

		this.otherUsername = "janedoe";
		this.otherPassword = "letmein";
	}

	@Test
	@Order(1)
	public void signupIntegrationTest() throws Exception {

		/* Check the Rest End Point Response */
		this.mockMvc
				.perform(MockMvcRequestBuilders.post("/user/signup").contentType(MediaType.APPLICATION_JSON)
						.content(this.objectMapper.writeValueAsString(this.user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.firstName", is(this.user.getFirstName())))
				.andExpect(jsonPath("$.lastName", is(this.user.getLastName())))
				.andExpect(jsonPath("$.username", is(this.user.getUsername())))
				.andExpect(jsonPath("$.phone", is(this.user.getPhone())))
				.andExpect(jsonPath("$.emailId", is(this.user.getEmailId())));

		/* Check the DB */
		Optional<User> opt = this.userRepository.findByUsername(this.user.getUsername());

		assertTrue(opt.isPresent(), "User Should Exist");

		// The id depends on the database state, so only check that one was generated.
		assertNotNull(opt.get().getUserId());
		assertEquals(this.user.getFirstName(), opt.get().getFirstName());
		assertEquals(this.user.getLastName(), opt.get().getLastName());
		assertEquals(this.user.getUsername(), opt.get().getUsername());
		assertEquals(this.user.getPhone(), opt.get().getPhone());
		assertEquals(this.user.getEmailId(), opt.get().getEmailId());
		assertEquals(false, opt.get().getEmailVerified());

		assertTrue(this.passwordEncoder.matches(this.user.getPassword(), opt.get().getPassword()));
	}

	@Test
	@Order(2)
	public void signupUsernameExistsIntegrationTest() throws Exception {

		/* Check the Rest End Point Response */
		this.mockMvc
				.perform(MockMvcRequestBuilders.post("/user/signup").contentType(MediaType.APPLICATION_JSON)
						.content(this.objectMapper.writeValueAsString(this.user)))
				.andExpect(status().is4xxClientError()).andExpect(jsonPath("$.httpStatusCode", is(400)))
				// Jackson 3 writes enums with toString(): "400 BAD_REQUEST" instead of
				// "BAD_REQUEST"
				.andExpect(jsonPath("$.httpStatus", is("400 BAD_REQUEST")))
				.andExpect(jsonPath("$.reason", is("BAD REQUEST"))).andExpect(jsonPath("$.message",
						is(String.format("Username already exists, %s", this.user.getUsername()))));
	}

	@Test
	@Order(3)
	public void signupEmailExistsIntegrationTest() throws Exception {

		this.user.setUsername(this.otherUsername);

		/* Check the Rest End Point Response */
		this.mockMvc
				.perform(MockMvcRequestBuilders.post("/user/signup").contentType(MediaType.APPLICATION_JSON)
						.content(this.objectMapper.writeValueAsString(this.user)))
				.andExpect(status().is4xxClientError()).andExpect(jsonPath("$.httpStatusCode", is(400)))
				.andExpect(jsonPath("$.httpStatus", is("400 BAD_REQUEST")))
				.andExpect(jsonPath("$.reason", is("BAD REQUEST"))).andExpect(
						jsonPath("$.message", is(String.format("Email already exists, %s", this.user.getEmailId()))));
	}

	@Test
	@Order(4)
	public void verifyEmailIntegrationTest() throws Exception {

		/* Check if the User exists */
		Optional<User> opt = this.userRepository.findByUsername(this.user.getUsername());
		assertTrue(opt.isPresent(), "User Should Exist");

		/* Check the user emailVerified flag initial value */
		assertEquals(false, opt.get().getEmailVerified());

		String jwt = String.format("Bearer %s", this.jwtService.generateJwtToken(this.user.getUsername(), 10_000));

		/* Check the Rest End Point Response */
		this.mockMvc.perform(MockMvcRequestBuilders.get("/user/verify/email").header(AUTHORIZATION, jwt))
				.andExpect(status().isOk());

		/* Check if the User exists */
		opt = this.userRepository.findByUsername(this.user.getUsername());
		assertTrue(opt.isPresent(), "User Should Exist");

		/* Check the user emailVerified flag was updated */
		assertEquals(true, opt.get().getEmailVerified());

	}

	@Test
	@Order(5)
	public void verifyEmailUsernameNotFoundIntegrationTest() throws Exception {

		/* Check if the User exists */
		Optional<User> opt = this.userRepository.findByUsername(this.otherUsername);
		assertTrue(opt.isEmpty(), "User Should Not Exist");

		String jwt = String.format("Bearer %s", this.jwtService.generateJwtToken(this.otherUsername, 10_000));

		/* Check the Rest End Point Response */
		this.mockMvc.perform(MockMvcRequestBuilders.get("/user/verify/email").header(AUTHORIZATION, jwt))
				.andExpect(status().is4xxClientError()).andExpect(jsonPath("$.httpStatusCode", is(400)))
				.andExpect(jsonPath("$.httpStatus", is("400 BAD_REQUEST")))
				.andExpect(jsonPath("$.reason", is("BAD REQUEST")))
				.andExpect(jsonPath("$.message", is(String.format("Username doesn't exist, %s", this.otherUsername))));

	}
}