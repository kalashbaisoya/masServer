package com.mas.masServer.config;

import java.util.List;
import java.util.Map;

// import org.springframework.http.HttpStatus;
// import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.mas.masServer.dto.EnrollRequest;
import com.mas.masServer.dto.EnrollResponse;
import com.mas.masServer.customException.BiometricEnrollmentException;
import com.mas.masServer.customException.ExternalServiceException;

@Component
public class BiomEnrollVerifyClient {

    private final WebClient webClient;

    public BiomEnrollVerifyClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public String enrollAndMerge(List<String> fingerprints) {

        EnrollRequest request = new EnrollRequest();
        request.setBase64Templates(fingerprints);

        try {
            EnrollResponse response = webClient
                    .post()
                    .uri("http://10.14.75.89:5188/api/fingerprint/enroll")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            res -> res.bodyToMono(String.class)
                                      .map(msg -> new BiometricEnrollmentException(
                                              "Invalid fingerprint data provided"
                                      ))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            res -> res.bodyToMono(String.class)
                                      .map(msg -> new ExternalServiceException(
                                              "Fingerprint service is currently unavailable"
                                      ))
                    )
                    .bodyToMono(EnrollResponse.class)
                    .block();

            if (response == null || response.getEnrolledTemplate() == null) {
                throw new BiometricEnrollmentException(
                        response != null && response.getMessage() != null
                                ? response.getMessage()
                                : "Fingerprint enrollment failed"
                );
            }

            return response.getEnrolledTemplate();

        } catch (WebClientResponseException e) {
            throw new ExternalServiceException(
                    "Unable to communicate with fingerprint service"
            );
        }
    }

    public boolean verify(String enrolledTemplate, String templateToVerify) {

        Map<String, String> body = Map.of(
                "enrolledTemplate", enrolledTemplate,
                "templateToVerify", templateToVerify
        );

        return webClient.post()
                .uri("http://10.14.75.89:5188/api/fingerprint/verify")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> Boolean.TRUE.equals(res.get("match")))
                .block();
    }
}


