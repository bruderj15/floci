package io.github.hectorvent.floci.services.opensearch;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.opensearch.OpenSearchClient;
import software.amazon.awssdk.services.opensearch.model.DomainStatus;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OpenSearchVersionsIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "OpenSearch_3.9", "OpenSearch_3.8", "OpenSearch_3.7",
            "OpenSearch_2.18", "OpenSearch_2.16", "OpenSearch_2.14", "OpenSearch_2.12",
            "OpenSearch_2.10", "OpenSearch_2.8", "OpenSearch_2.6", "OpenSearch_2.4",
            "OpenSearch_2.2", "OpenSearch_2.1", "OpenSearch_2.0", "OpenSearch_1.1", "OpenSearch_1.0"
    })
    void sdkListsCreatesAndDescribesAddedVersions(String engineVersion) {
        String domainName = "version-" + engineVersion.substring("OpenSearch_".length()).replace('.', '-');
        try (OpenSearchClient client = OpenSearchClient.builder()
                .endpointOverride(URI.create("http://localhost:" + RestAssured.port))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
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
}
