import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class GitlabAccess {


	private String id;
	private String companyId;
	private String host;
	private String pat;

	public void setHost(String rawHost) {
		this.host = normalizeAndValidateHost(rawHost);
	}

	public String getHost() {
		return this.host;
	}

	public String getPat() {
		return this.pat;
	}

	public void setPat(String pat) {
		this.pat = pat;
	}


	private static final Pattern SAFE_SUBPATH_PATTERN = Pattern.compile("^(/[a-zA-Z0-9_-]+)*$");
	private static final Pattern HAS_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
	private static final Pattern API_V4_SUFFIX_PATTERN = Pattern.compile("(?i)/api/v4(/.*)?$");

	/**
	 * Validates and normalizes a GitLab base URL into a canonical HTTPS base URL without a trailing slash.
	 * - If no scheme is given, url defaults to https://
	 * - Strips any /api/v4 suffix (including its query/fragment)
	 * - Rejects credentials, query/fragment parameters, and unsafe path segments (e.g., '..' or '%').
	 */
	private String normalizeAndValidateHost(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException();
		}

		url = url.trim();
		url = HAS_SCHEME_PATTERN.matcher(url).matches() ? url : "https://" + url;

		final URI uri = URI.create(url);

		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException();
		}

		if (uri.getHost() == null || uri.getUserInfo() != null) {
			throw new IllegalArgumentException();
		}

		String path = uri.getRawPath() == null ? "" : uri.getRawPath();
		var apiMatcher = API_V4_SUFFIX_PATTERN.matcher(path);
		boolean hasApiV4Suffix = apiMatcher.find();

		if (!hasApiV4Suffix && (uri.getRawQuery() != null || uri.getRawFragment() != null)) {
			throw new IllegalArgumentException();
		}

		String normalizedPath;
		if (hasApiV4Suffix) {
			normalizedPath = apiMatcher.replaceFirst("");
		} else if (path.endsWith("/")) {
			normalizedPath = path.substring(0, path.length() - 1);
		} else {
			normalizedPath = path;
		}

		// Path may only contain simple segments (a-z, 0-9, -, _).
		if (!SAFE_SUBPATH_PATTERN.matcher(normalizedPath).matches()) {
			throw new IllegalArgumentException();
		}

		int port = uri.getPort();
		String portPart = (port != -1 && port != 443) ? ":" + port : "";
		return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + portPart + normalizedPath;
	}

	/**
	 * Checks whether the given URL matches this GitLab host, port, and subpath
	 * while rejecting any path traversal or percent-encoding attempts.
	 */
	public boolean isUrlWhitelisted(String targetUrlStr) {
		if (targetUrlStr == null || this.host == null) {
			return false;
		}
		try {
			URI target = new URI(targetUrlStr);
			URI allowed = URI.create(this.host);

			int allowedPort = allowed.getPort() == -1 ? 443 : allowed.getPort();
			int targetPort = target.getPort() == -1 ? 443 : target.getPort();

			// 1. Scheme, host, and port must match; no userinfo allowed
			if (!"https".equalsIgnoreCase(target.getScheme())
					|| allowedPort != targetPort
					|| target.getHost() == null
					|| !allowed.getHost().equals(target.getHost().toLowerCase(Locale.ROOT))
					|| target.getUserInfo() != null) {
				return false;
			}

			// 2. Reject path traversals, double slashes, percent-encoding, and unnormalized "./" segments
			String path = target.getRawPath();
			if (path.contains("..") || path.contains("%")
					|| !path.equals(target.normalize().getRawPath())) {
				return false;
			}

			// 3. Target path must start with "<allowedPath>/"
			return path.startsWith(allowed.getRawPath() + "/");
		} catch (URISyntaxException e) {
			return false;
		}
	}

	/**
	 * Baut einen GitLab-API-v4-Endpunkt sicher auf Basis von this.host zusammen.
	 */
	public URI buildGitlabApiUrl(String apiPath) {
		if (this.host == null) {
			throw new IllegalStateException("GitlabAccess.host ist nicht gesetzt.");
		}
		if (apiPath == null || apiPath.isBlank()) {
			throw new IllegalArgumentException("apiPath darf nicht leer sein.");
		}

		// Führende Slashes entfernen
		String cleanApiPath = apiPath.replaceFirst("^/+", "");
		String fullUrl = this.host + "/api/v4/" + cleanApiPath;

		if (!isUrlWhitelisted(fullUrl)) {
			throw new SecurityException("Ziel-URL verlässt den erlaubten GitLab-Host: " + fullUrl);
		}

		return URI.create(fullUrl);
	}
}
