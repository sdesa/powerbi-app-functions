package com.function;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.model.EmbedConfig;
import com.model.EmbedToken;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

	// Common configuration properties for both authentication types

	// Credentials of the Service Principal and other required attributes to access the REST APIs, replace with yours
	public static final String clientId = "clientId_of_your_service_principal";
	public static final String tenantId = "your_tenant_id";
	public static final String appSecret = "client_secret_of_your_sp";

	// DO NOT CHANGE
	public static final String authorityUrl = "https://login.microsoftonline.com/";
	public static final String scopeUrl = "https://analysis.windows.net/powerbi/api/.default";

	@FunctionName("token")
	public HttpResponseMessage run(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
			HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) throws MalformedURLException, InterruptedException, ExecutionException {
		context.getLogger().info("Java HTTP trigger processed a request.");

		// this function will be invoked by passing the reportId and groupId as query parameters
		String reportId = request.getQueryParameters().get("reportId");
		String groupId = request.getQueryParameters().get("groupId");
		
		String accessToken = getAccessTokenUsingServicePrincipal(context);

		EmbedConfig reportEmbedConfig = getReportEmbedDetails(reportId, groupId, accessToken);	

		// Get embed token
		reportEmbedConfig.embedToken = getMultiResourceEmbedToken(reportEmbedConfig.reportId, reportEmbedConfig.datasetId, accessToken);
		
		// Return JSON response in string
		JSONObject responseObj = new JSONObject();
		responseObj.put("embedToken", reportEmbedConfig.embedToken.token);
		responseObj.put("embedUrl", reportEmbedConfig.embedUrl);
		responseObj.put("tokenExpiry", reportEmbedConfig.embedToken.expiration);
		
		String response = responseObj.toString();

		return request.createResponseBuilder(HttpStatus.OK).body(response).build();

	}

	private static String getAccessTokenUsingServicePrincipal(final ExecutionContext context)
			throws MalformedURLException, InterruptedException, ExecutionException {
		
		// Build ConfidentialClientApp
		ConfidentialClientApplication app = ConfidentialClientApplication
				.builder(clientId, ClientCredentialFactory.createFromSecret(appSecret))
				.authority(authorityUrl + tenantId).build();

		ClientCredentialParameters clientCreds = ClientCredentialParameters
				.builder(Collections.singleton(scopeUrl)).build();

		// Acquire new AAD token
		IAuthenticationResult result = app.acquireToken(clientCreds).get();

		// Return access token if token is acquired successfully
		if (result != null && result.accessToken() != null && !result.accessToken().isEmpty()) {
			context.getLogger().info("Authenticated with Service Principal mode");
			
			return result.accessToken();
		} else {
			context.getLogger().info("Failed to authenticate with Service Principal mode");
			return null;
		}

	}

	public static EmbedConfig getReportEmbedDetails(String reportId, String groupId, String accessToken) {
		if (StringUtils.isEmpty(reportId)) {
			throw new RuntimeException("Empty Report Id");
		}
		if (StringUtils.isEmpty(groupId)) {
			throw new RuntimeException("Empty Group(Workspace) Id");
		}
		
		// Get Report In Group API: https://api.powerbi.com/v1.0/myorg/groups/{groupId}/reports/{reportId}
		StringBuilder urlStringBuilder = new StringBuilder("https://api.powerbi.com/v1.0/myorg/groups/"); 
		urlStringBuilder.append(groupId);
		urlStringBuilder.append("/reports/");
		urlStringBuilder.append(reportId);
		
		// REST API URL to get report details
		String endPointUrl = urlStringBuilder.toString();
		
		// Request header
    	HttpHeaders reqHeader = new HttpHeaders();
    	reqHeader.put("Content-Type", Arrays.asList("application/json"));
    	reqHeader.put("Authorization", Arrays.asList("Bearer " + accessToken));
    	
    	// HTTP entity object - holds header and body
		HttpEntity <String> reqEntity = new HttpEntity <> (reqHeader);
		
		// Rest API get report's details
		RestTemplate getReportRestTemplate = new RestTemplate();
		ResponseEntity<String> response = getReportRestTemplate.exchange(endPointUrl, org.springframework.http.HttpMethod.GET, reqEntity, String.class);
		
		String responseBody = response.getBody();
		
		// Create embedding configuration object
		EmbedConfig reportEmbedConfig = new EmbedConfig();
		
		// Parse JSON and get Report details
		org.json.JSONObject responseObj = new org.json.JSONObject(responseBody);
		reportEmbedConfig.embedUrl = responseObj.getString("embedUrl");
		reportEmbedConfig.datasetId = responseObj.getString("datasetId");
		reportEmbedConfig.reportId = responseObj.getString("id");
		
		return reportEmbedConfig;
	}
	
	/**
	 * Get embed token for multiple workspaces, datasets, and reports. 
	 * @see <a href="https://aka.ms/MultiResourceEmbedToken">Multi-Resource Embed Token</a>
	 * @param reportId
	 * @param datasetId
	 * @return EmbedToken 
	 */
	public static EmbedToken getMultiResourceEmbedToken(String reportId, String datasetId, String accessToken) {
		// Embed Token - Generate Token REST API
		String uri = "https://api.powerbi.com/v1.0/myorg/GenerateToken";
		
		RestTemplate restTemplate = new RestTemplate();
		
		// Create request header
    	HttpHeaders headers = new HttpHeaders();
    	headers.put("Content-Type", Arrays.asList("application/json"));
    	headers.put("Authorization", Arrays.asList("Bearer " + accessToken));
    	headers.put("Accept", Arrays.asList(""));
    	
    	// Request body
    	JSONObject requestBody = new JSONObject();
    	
    	// Add dataset id in body
    	JSONArray jsonDatasets = new JSONArray();
    	jsonDatasets.put(new JSONObject().put("id", datasetId));
    	
    	// Add report id in body
    	JSONArray jsonReports = new JSONArray();
    	jsonReports.put(new JSONObject().put("id", reportId));
    	
    	requestBody.put("datasets", jsonDatasets);
		requestBody.put("reports", jsonReports);
		
    	// Add (body, header) to HTTP entity
		HttpEntity <String> httpEntity = new HttpEntity<>(requestBody.toString(), headers);
		
		// Call the API
		ResponseEntity<String> response = restTemplate.postForEntity(uri, httpEntity, String.class);
		String responseBody = response.getBody();
		
		// Parse responseBody
		org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody); 
		
		String token = jsonResponse.getString("token");
		String tokenId = jsonResponse.getString("tokenId");
		String expiration = jsonResponse.getString("expiration");

		EmbedToken embedToken = new EmbedToken(token, tokenId, expiration);
		
		return embedToken;
	}

}
