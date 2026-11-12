package com.xebyte;

import junit.framework.TestCase;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;


/**
 * Functional tests for GhidraMCPPlugin HTTP server endpoints.
 *
 * This test suite validates the MCP server functionality by testing
 * HTTP endpoints directly against a running Ghidra instance with the plugin.
 *
 * Prerequisites:
 * - Ghidra must be running with GhidraMCPPlugin enabled
 * - A program must be loaded in Ghidra
 * - MCP HTTP server must be started (Tools > GhidraMCP > Start MCP Server)
 */
public class GhidraMCPPluginTest extends TestCase {

    private static final String BASE_URL = "http://127.0.0.1:8089";
    private static final int TIMEOUT_SECONDS = 10;
    private HttpClient httpClient;
    private static boolean serverAvailabilityChecked = false;
    private static boolean serverAvailable = false;

    @Override
    protected void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();
            
        // Check server availability once per test run
        if (!serverAvailabilityChecked) {
            serverAvailable = liveTestsEnabled() && checkServerAvailability();
            serverAvailabilityChecked = true;
            if (liveTestsEnabled()) assertTrue("Requested live server is unavailable", serverAvailable);
            if (!serverAvailable) {
                System.out.println("=== LIVE MCP SERVER TESTS DISABLED OR NOT AVAILABLE ===");
                System.out.println("Integration tests will be skipped. To run these tests:");
                System.out.println("0. Set GHIDRA_MCP_LIVE_TESTS=1");
                System.out.println("1. Start Ghidra");
                System.out.println("2. Load a program");
                System.out.println("3. Enable GhidraMCP plugin");
                System.out.println("4. Start MCP server (Tools > GhidraMCP > Start MCP Server)");
                System.out.println("5. Re-run tests");
                System.out.println("===================================");
            }
        }
    }

    private boolean liveTestsEnabled() {
        String value = System.getenv("GHIDRA_MCP_LIVE_TESTS");
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
    }
    
    /**
     * Check if MCP server is available for testing (one-time check)
     */
    private boolean checkServerAvailability() {
        // Try multiple times to account for transient network issues
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/check_connection"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body() != null && response.body().contains("server_kind")) {
                    return true;
                }
            } catch (Exception e) {
                // If this is the last attempt, log the error
                if (attempt == 3) {
                    System.out.println("Server availability check failed after 3 attempts: " + e.getMessage());
                }
            }
            
            // Short delay between attempts
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    @Override
    protected void tearDown() {
        // Clean up resources if needed
    }

    /**
     * Helper method to make HTTP GET requests to endpoints
     */
    private HttpResponse<String> makeGetRequest(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/" + endpoint))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .GET()
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Helper method to make HTTP POST requests to endpoints
     */
    private HttpResponse<String> makePostRequest(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/" + endpoint))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Functional interface for test execution
     */
    @FunctionalInterface
    private interface TestExecutor {
        void execute() throws Exception;
    }

    /**
     * Run test only if server is available, otherwise skip with message
     */
    private void runIfServerAvailable(String testName, TestExecutor testCode) {
        if (!serverAvailable) {
            // Server not available, skip test silently (message already shown in setUp)
            return;
        }
        
        try {
            testCode.execute();
        } catch (Exception e) {
            // Check if this is a connectivity issue
            String errorMsg = e.getMessage();
            if (!liveTestsEnabled() && errorMsg != null && (errorMsg.contains("HTTP/1.1 header parser received no bytes") ||
                                   errorMsg.contains("Connection refused") ||
                                   errorMsg.contains("ConnectException") ||
                                   errorMsg.contains("SocketTimeoutException"))) {
                // This is a connectivity issue, skip the test
                System.out.println("SKIP: " + testName + " - Server connectivity issue during test execution: " + errorMsg);
                return;
            }
            // If it's not a connectivity issue, fail the test
            fail(testName + " failed: " + e.getMessage());
        }
    }

    /**
     * Test basic server connectivity
     */
    public void testServerConnectivity() throws Exception {
        runIfServerAvailable("testServerConnectivity", () -> {
            HttpResponse<String> response = makeGetRequest("find_functions");
            assertTrue("Server should respond successfully", response.statusCode() == 200);
            assertNotNull("Response body should not be null", response.body());
        });
    }

    /**
     * Test all basic listing endpoints that should always work
     */
    public void testBasicListingEndpoints() throws Exception {
        runIfServerAvailable("testBasicListingEndpoints", () -> {
            String[] basicEndpoints = {
                "find_functions", "find_functions", "list_program_items?kind=classes", "list_program_items?kind=segments",
                "list_program_items?kind=imports", "list_program_items?kind=exports", "list_program_items?kind=namespaces", "list_program_items?kind=data_items", "list_strings"
            };

            for (String endpoint : basicEndpoints) {
                HttpResponse<String> response = makeGetRequest(endpoint);
                assertEquals("Endpoint " + endpoint + " should return 200", 200, response.statusCode());
                assertNotNull("Response body should not be null for " + endpoint, response.body());
                assertFalse("Response should not be empty for " + endpoint, response.body().trim().isEmpty());
            }
        });
    }

    /**
     * Test search functionality endpoints
     */
    public void testSearchEndpoints() throws Exception {
        if (!serverAvailable) return;
        // Test function search with a common term
        HttpResponse<String> response = makeGetRequest("find_functions?query=get&limit=5");
        assertEquals("Function search should return 200", 200, response.statusCode());

        // Test with empty query (should handle gracefully)
        response = makeGetRequest("find_functions?query=&limit=5");
        assertTrue("Empty search should return 200 or 400",
            response.statusCode() == 200 || response.statusCode() == 400);
    }

    /**
     * Test decompilation endpoints
     */
    public void testDecompilationEndpoints() throws Exception {
        if (!serverAvailable) return;
        HttpResponse<String> response = makeGetRequest("get_functions?function=main");
        assertEquals("Decompilation should return 200", 200, response.statusCode());
        assertTrue("Expected real decompiled C", response.body().contains("decompiled_code"));

    }

    /**
     * Test cross-reference endpoints
     */
    public void testCrossReferenceEndpoints() throws Exception {
        if (!serverAvailable) return;
        // Test with a known address format (these should at least not crash)
        String[] xrefEndpoints = {
            "get_xrefs_to?address=0x034c1000&limit=5",
            "get_xrefs_from?address=0x034c1000&limit=5"
        };

        for (String endpoint : xrefEndpoints) {
            HttpResponse<String> response = makeGetRequest(endpoint);
            assertTrue("XRef endpoint " + endpoint + " should return 200 or handle gracefully",
                response.statusCode() == 200 || response.statusCode() == 400);
        }
    }

    /**
     * Test current state endpoints
     */
    public void testCurrentStateEndpoints() throws Exception {
        if (!serverAvailable) return;
        String[] stateEndpoints = {
            "get_ui_cursor"
        };

        for (String endpoint : stateEndpoints) {
            HttpResponse<String> response = makeGetRequest(endpoint);
            assertEquals("State endpoint " + endpoint + " should return 200", 200, response.statusCode());
            assertNotNull("Response should not be null for " + endpoint, response.body());
        }
    }

    /**
     * Test the problematic endpoints that were failing (404 errors)
     */
    public void testProblematicEndpoints() throws Exception {
        if (!serverAvailable) return;
        String[] problematicEndpoints = {
            "list_globals", "get_metadata", "search_byte_patterns",
            "analyze_call_graph", "list_globals", "list_strings"
        };

        int workingCount = 0;
        int totalCount = problematicEndpoints.length;

        for (String endpoint : problematicEndpoints) {
            try {
                HttpResponse<String> response = makeGetRequest(endpoint + (endpoint.contains("?") ? "&" : "?") + "limit=5");
                if (response.statusCode() == 200) {
                    workingCount++;
                    System.out.println("✓ " + endpoint + " is working");
                } else if (response.statusCode() == 404) {
                    System.out.println("✗ " + endpoint + " returns 404 (not implemented)");
                } else {
                    System.out.println("? " + endpoint + " returns " + response.statusCode());
                }
            } catch (Exception e) {
                System.out.println("✗ " + endpoint + " failed with exception: " + e.getMessage());
            }
        }

        System.out.println("Problematic endpoints working: " + workingCount + "/" + totalCount);

        // This test documents the current state but doesn't fail if endpoints are missing
        // In a perfect world, all endpoints should work (workingCount == totalCount)
        assertTrue("All current advanced endpoint fixtures should be accessible", workingCount == totalCount);
    }

    /**
     * Test endpoint parameter validation
     */
    public void testParameterValidation() throws Exception {
        runIfServerAvailable("testParameterValidation", () -> {
            // Test invalid limit parameters
            HttpResponse<String> response = makeGetRequest("find_functions?limit=-1");
            assertTrue("Invalid limit should be handled gracefully",
                response.statusCode() == 200 || response.statusCode() == 400);

            // Test invalid offset parameters
            response = makeGetRequest("find_functions?offset=-5");
            assertTrue("Invalid offset should be handled gracefully",
                response.statusCode() == 200 || response.statusCode() == 400);

            // Test very large limit (should be capped)
            response = makeGetRequest("find_functions?limit=999999");
            assertEquals("Large limit should still return 200", 200, response.statusCode());
        });
    }

    /**
     * Test error handling for non-existent endpoints
     */
    public void testNonExistentEndpoints() throws Exception {
        if (!serverAvailable) return;
        String[] fakeEndpoints = {
            "nonexistent", "fake_endpoint", "test123"
        };

        for (String endpoint : fakeEndpoints) {
            HttpResponse<String> response = makeGetRequest(endpoint);
            assertEquals("Non-existent endpoint should return 404", 404, response.statusCode());
            assertTrue("404 response should contain error message",
                response.body().contains("404") || response.body().contains("Not Found"));
        }
    }

    /**
     * Test HTTP method validation
     */
    public void testHttpMethodValidation() throws Exception {
        if (!serverAvailable) return;
        // v7 consolidated decompilation into the documented GET query.
        HttpResponse<String> response = makeGetRequest("get_functions?function=main");
        assertEquals("The documented GET method should succeed", 200, response.statusCode());
        assertTrue("GET must execute decompilation", response.body().contains("decompiled_code"));
    }

    /**
     * Performance test for basic endpoints
     */
    public void testBasicPerformance() throws Exception {
        if (!serverAvailable) return;
        long startTime = System.currentTimeMillis();

        // Make several quick requests
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = makeGetRequest("find_functions?limit=10");
            assertEquals("Performance test request " + i + " should succeed", 200, response.statusCode());
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("5 requests completed in " + totalTime + "ms");
        assertTrue("Basic performance should be reasonable (< 10 seconds)", totalTime < 10000);
    }

    /**
     * Integration test that combines multiple endpoints
     */
    public void testEndpointIntegration() throws Exception {
        if (!serverAvailable) return;
        // Get function list
        HttpResponse<String> functionsResponse = makeGetRequest("find_functions?limit=1");
        assertEquals("Should get functions list", 200, functionsResponse.statusCode());

        // Get segments
        HttpResponse<String> segmentsResponse = makeGetRequest("list_program_items?kind=segments");
        assertEquals("Should get segments list", 200, segmentsResponse.statusCode());

        // Get UI cursor
        HttpResponse<String> addressResponse = makeGetRequest("get_ui_cursor");
        assertEquals("Should get UI cursor", 200, addressResponse.statusCode());

        // All responses should be non-empty
        assertFalse("Functions response should not be empty", functionsResponse.body().trim().isEmpty());
        assertFalse("Segments response should not be empty", segmentsResponse.body().trim().isEmpty());
        assertFalse("Address response should not be empty", addressResponse.body().trim().isEmpty());
    }

    /**
     * Test comprehensive endpoint coverage
     */
    public void testEndpointCoverage() throws Exception {
        if (!serverAvailable) return;
        String[] allExpectedEndpoints = {
            // Basic listings
            "find_functions", "find_functions", "list_program_items?kind=classes", "list_program_items?kind=segments", "list_program_items?kind=imports", "list_program_items?kind=exports",
            "list_program_items?kind=namespaces", "list_program_items?kind=data_items", "list_strings",
            // Search and analysis
            "find_functions", "get_functions?function=main", "get_functions", "disassemble_function",
            // Cross-references
            "get_xrefs_to", "get_xrefs_from", "get_functions?function=main&fields=callers,callees",
            // Current state
            "get_ui_cursor",
            // Advanced features (may be missing)
            "list_globals", "get_metadata", "search_byte_patterns", "analyze_call_graph",
            "list_globals", "list_strings"
        };

        int workingEndpoints = 0;
        int totalEndpoints = allExpectedEndpoints.length;

        System.out.println("\n=== Endpoint Coverage Report ===");

        for (String endpoint : allExpectedEndpoints) {
            try {
                HttpResponse<String> response = makeGetRequest(endpoint + (endpoint.contains("?") ? "&" : "?") + "limit=1");
                if (response.statusCode() == 200) {
                    System.out.println("✓ " + endpoint);
                    workingEndpoints++;
                } else if (response.statusCode() == 404) {
                    System.out.println("✗ " + endpoint + " (404 - not implemented)");
                } else {
                    System.out.println("? " + endpoint + " (" + response.statusCode() + ")");
                }
            } catch (Exception e) {
                System.out.println("✗ " + endpoint + " (exception: " + e.getMessage() + ")");
            }
        }

        double coveragePercent = (double) workingEndpoints / totalEndpoints * 100;
        System.out.println("\nCoverage: " + workingEndpoints + "/" + totalEndpoints +
                          " (" + String.format("%.1f", coveragePercent) + "%)");

        // Every current route fixture must respond.
        assertTrue("All current endpoint fixtures should be accessible", workingEndpoints == totalEndpoints);
    }
}
