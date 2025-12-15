package com.insurancemegacorp.sense;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration that provides mock Spring AI components.
 * This allows tests to run without a real OpenAI API key.
 */
@TestConfiguration
public class TestAIConfiguration {

    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder() {
        // Create a mock ChatClient.Builder
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        // Set up the mock chain
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.prompt()).thenReturn(mockRequestSpec);
        when(mockRequestSpec.user(any(String.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallSpec);

        // Return a JSON response that the IntentClassifier can parse
        String mockResponse = """
            {
              "intent": "NORMAL",
              "confidence": 0.75,
              "explanation": "Test mock response",
              "factors": ["mock_factor"]
            }
            """;
        when(mockCallSpec.content()).thenReturn(mockResponse);

        return mockBuilder;
    }
}
