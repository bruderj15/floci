package io.github.hectorvent.floci.services.opensearch;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.opensearch.OpenSearchClient;
import software.amazon.awssdk.services.opensearch.model.DomainStatus;
import software.amazon.awssdk.services.opensearch.model.OpenSearchException;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OpenSearchVersionsIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"OpenSearch_3.7", "OpenSearch_1.1", "OpenSearch_1.0"})
    void sdkListsCreatesAndDescribesAddedVersions(String engineVersion) {
        String domainName = "version-" + engineVersion.substring("OpenSearch_".length()).replace('.', '-');
        try (OpenSearchClient client = newClient()) {
            assertTrue(client.listVersions(request -> request.maxResults(100)).versions().contains(engineVersion));
            DomainStatus created = client.createDomain(request -> request
                    .domainName(domainName).engineVersion(engineVersion)).domainStatus();
            try {
                assertEquals(engineVersion, created.engineVersion());
                assertEquals(engineVersion, client.describeDomain(request -> request.domainName(domainName))
                        .domainStatus().engineVersion());
                assertEquals(engineVersion, client.describeDomainConfig(request -> request.domainName(domainName))
                        .domainConfig().engineVersion().options());
            } finally {
                client.deleteDomain(request -> request.domainName(domainName));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OpenSearch_3.9", "OpenSearch_3.8", "OpenSearch_3.4", "OpenSearch_3.2", "OpenSearch_3.0",
            "OpenSearch_2.18", "OpenSearch_2.16", "OpenSearch_2.14", "OpenSearch_2.12",
            "OpenSearch_2.10", "OpenSearch_2.8", "OpenSearch_2.6", "OpenSearch_2.4",
            "OpenSearch_2.2", "OpenSearch_2.1", "OpenSearch_2.0"
    })
    void sdkRejectsVersionsUnavailableOnAws(String engineVersion) {
        String domainName = "rejected-" + engineVersion.substring("OpenSearch_".length()).replace('.', '-');
        try (OpenSearchClient client = newClient()) {
            assertFalse(client.listVersions(request -> request.maxResults(100)).versions().contains(engineVersion));
            assertValidationException(() -> client.createDomain(request -> request
                    .domainName(domainName).engineVersion(engineVersion)));

            client.createDomain(request -> request.domainName(domainName).engineVersion("OpenSearch_2.19"));
            try {
                given()
                    .contentType("application/json")
                    .header("Authorization", "AWS4-HMAC-SHA256 Credential=test/20260101/us-east-1/es/aws4_request")
                    .body("{\"EngineVersion\":\"" + engineVersion + "\"}")
                .when()
                    .post("/2021-01-01/opensearch/domain/" + domainName + "/config")
                .then()
                    .statusCode(400)
                    .body("__type", containsString("ValidationException"));
                assertValidationException(() -> client.upgradeDomain(request -> request
                        .domainName(domainName).targetVersion(engineVersion)));
                assertEquals("OpenSearch_2.19", client.describeDomain(request -> request.domainName(domainName))
                        .domainStatus().engineVersion());
            } finally {
                client.deleteDomain(request -> request.domainName(domainName));
            }
        }
    }

    private static OpenSearchClient newClient() {
        return OpenSearchClient.builder()
                .endpointOverride(URI.create("http://localhost:" + RestAssured.port))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
    }

    private static void assertValidationException(Executable request) {
        OpenSearchException exception = assertThrows(OpenSearchException.class, request);
        assertEquals(400, exception.statusCode());
        assertEquals("ValidationException", exception.awsErrorDetails().errorCode());
    }
}
