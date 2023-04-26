package cz.coffee.core.requests;

import com.google.gson.*;
import cz.coffee.skript.requests.EffExecuteRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static cz.coffee.SkJson.console;

public abstract class HttpHandler {
    public static HttpHandler of(final String url, final String method) {
        return new HttpHandler(url, method) {
            @Override
            public void asyncSend() {
                super.asyncSend();
            }
        };
    }

    public static class RequestContent {
        private final List<String> keys;
        private final List<String> values;

        RequestContent(List<String> keys, List<String> values) {
            this.keys = keys;
            this.values = values;
        }

        RequestContent(JsonElement json) {
            if (json.isJsonObject()) {
                this.keys = json.getAsJsonObject().keySet().stream().toList();
                this.values = json.getAsJsonObject().asMap().values().stream().map(JsonElement::toString).map(EffExecuteRequest::sanitize).collect(Collectors.toList());
            } else {
                this.keys = new ArrayList<>();
                this.values = new ArrayList<>();
            }
        }

        public List<String> getKeys() {
            return keys;
        }

        public List<String> getValues() {
            return values;
        }

        public static RequestContent process(Object o, boolean fullJson) {
            JsonElement json = JsonNull.INSTANCE;
            List<String> values = new ArrayList<>(), keys = new ArrayList<>();

            if (fullJson) {
                JsonElement[] e = (JsonElement[]) o;
                for (JsonElement jsonElement : e) {
                    return new RequestContent(jsonElement);
                }
                return null;
            } else {
                if (o.toString().startsWith("{") && o.toString().endsWith("}")) {
                    // JSON
                    if (o instanceof String str) {
                        try {
                            json = JsonParser.parseString(str);
                        } catch (Exception e) {
                            return null;
                        }
                    } else if (o instanceof JsonElement element) {
                        json = element;
                    }
                    if (json.isJsonObject()) {
                        json.getAsJsonObject().entrySet().forEach(entry -> {
                            values.add(EffExecuteRequest.sanitize(entry.getValue()));
                            keys.add(entry.getKey());
                        });
                    }
                } else {
                    // PAIRS
                    List<Object> objects = Arrays.asList((Object[]) o);
                    objects.forEach(object -> {
                        String str = object.toString();
                        String[] pairs = str.split("(:|: )");
                        values.add(pairs[1]);
                        keys.add(pairs[0]);
                    });
                }

                return new RequestContent(keys, values);
            }
        }

        @Override
        public String toString() {
            if (keys.size() != values.size()) throw new IllegalArgumentException("Lists must have same length");
            StringBuilder resultBuilder = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                resultBuilder.append(keys.get(i)).append("=").append(values.get(i));
                if (i < keys.size() - 1) resultBuilder.append(", ");
            }
            return "RequestContent{"+resultBuilder+"}";
        }


        @SuppressWarnings("rawtypes")
        public Set entrySet() {
            if (values.size() == keys.size()) {
                Map<String, String> entry = new WeakHashMap<>();
                for (int i = 0; i < values.size(); i++) {
                    entry.put(keys.get(i), values.get(i));
                }
                return entry.entrySet();
            }
            return null;
        }

        public Map<String, String> flatMap() {
            if (values.size() == keys.size()) {
                Map<String, String> entry = new WeakHashMap<>();
                for (int i = 0; i < values.size(); i++) {
                    entry.put(keys.get(i), values.get(i));
                }
                return entry;
            }
            return null;
        }
    }
    private String _method;
    private final Timer _timer;
    private java.net.http.HttpClient _client;
    private HttpRequest.Builder _requestBuilder;
    private JsonObject _content = new JsonObject();
    private final WeakHashMap<String, String> _headers = new WeakHashMap<>();
    protected String[] allowedMethods = {"GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "MOCK"};
    private Response _response;
    private HttpRequest _request;


    public HttpHandler(String url, String method) {
        _response = null;
        _method = method;
        _timer = null;
        _client = java.net.http.HttpClient.newHttpClient();
        URI _uri = null;
        try {
            _uri = URI.create(url.replaceAll(" ", "%20"));
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        _requestBuilder = HttpRequest.newBuilder().uri(_uri);
    }
    public boolean isSuccessful() {
        return _response.getStatusCode() == 200;
    }

    private void setHeaders() {
        if (!_headers.isEmpty() && _requestBuilder != null) {
            _headers.forEach(_requestBuilder::header);
        }
    }

    public Response getAll() {
        return _response;
    }

    private void jsonEncoded() {
        _requestBuilder.header("Content-type", "application/json");
    }


    @SuppressWarnings("UnusedReturnValue")
    public HttpHandler setBodyContent(RequestContent body) {
        final Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();
        _content = gson.toJsonTree(body.flatMap(), Map.class).getAsJsonObject();
        return this;
    }

    @Future.Method
    public Timer getTimer() {
        return _timer;
    }


    @SuppressWarnings("UnusedReturnValue")
    public HttpHandler setMainHeaders(RequestContent headers) {
        _headers.putAll(headers.flatMap());
        return this;
    }

    private HttpRequest makeRequest() {
        final String _METHOD = _method.toUpperCase();
        if (!Arrays.asList(allowedMethods).contains(_METHOD)) {
            console("Invalid request method &c" + _METHOD + "&r allowed methods are &f" + Arrays.toString(allowedMethods));
            return null;
        }
        if (_requestBuilder != null) {
            _request =  switch (_method.toUpperCase()) {
                case "GET" -> _requestBuilder.GET().build();
                case "POST" ->  {
                    jsonEncoded();
                    yield _requestBuilder.POST(HttpRequest.BodyPublishers.ofString(_content.toString())).build();
                }
                case "PUT" -> _requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(_content.toString())).build();
                case "DELETE" -> _requestBuilder.DELETE().build();
                case "HEAD" -> _requestBuilder.HEAD().build();
                case "PATCH" -> {
                    jsonEncoded();
                    yield _requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(_content.toString())).build();
                }
                default -> _requestBuilder.build();
            };
        }
        return _request;
    }


    public void asyncSend() {
        setHeaders();
        HttpRequest request = makeRequest();
        long startTime = System.nanoTime();
        CompletableFuture<HttpResponse<String>> future = _client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        long endTime = System.nanoTime();
        if (_timer != null) _timer.addTime(endTime - startTime);
        try {
            HttpResponse<String> result = future.get();
            _response = Response.of(request.headers(), result.body(), result.headers(), result.uri(), result.statusCode());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

/*    public void send() throws Exception {
        setHeaders();
        HttpRequest request = makeRequest();
        long startTime = System.nanoTime();
        HttpResponse<String> response = _client.send(request, HttpResponse.BodyHandlers.ofString());
        long endTime = System.nanoTime();
        if (_timer != null) {
            _timer.addTime(endTime - startTime);
        }
        _response = Response.of(request.headers(), response.body(), response.headers(), response.uri(), response.previousResponse(), response.statusCode());
    }
*/

    public void disconnect() {
        if (_client == null && _requestBuilder == null) {
            return;
        }
        _client = null;
        _requestBuilder = null;
        _method = null;
        _response = null;
    }

    public interface Response {
        static Response of(HttpHeaders connHeaders, String body, HttpHeaders headers, URI uri, int statusCode) {
            return new Response() {
                @Override
                public String toString() {
                    return "HttpHandler.Response{body="+body+", uri="+uri+", statusCode="+statusCode+"}";
                }
                @Override
                public String rawBody() {
                    return body;
                }

                @Override
                public String connHeaders(boolean jsonEncoded) {
                    if (jsonEncoded) {
                        return new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create().toJson(headers.map());
                    } else {
                        return headers.toString();
                    }
                }

                @Override
                public String headers(boolean jsonEncoded) {
                    if (jsonEncoded) {
                        return new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create().toJson(connHeaders.map());
                    } else {
                        return connHeaders.toString();
                    }
                }

                @Override
                public int getStatusCode() {
                    return statusCode;
                }

                @Override
                public URL getUrl() throws MalformedURLException {
                    return uri.toURL();
                }
            };
        }

        String rawBody();
        String connHeaders(boolean jsonEncoded);
        String headers(boolean jsonEncoded);
        int getStatusCode();
        URL getUrl() throws MalformedURLException;
        String toString();
    }
}
class HtmlToJson {
    public static JsonObject of(String html) {
        Document doc = Jsoup.parse(html);

        JsonObject json = new JsonObject();
        Element root = doc.child(0);
        json.add(root.tagName(), elementToJson(root));

        return json;
    }

    private static JsonObject elementToJson(Element element) {
        JsonObject json = new JsonObject();

        // Add element attributes to JSON object
        for (org.jsoup.nodes.Attribute attr : element.attributes()) {
            json.addProperty(attr.getKey(), attr.getValue());
        }

        // Add element content to JSON object
        String content = element.ownText().trim();
        if (!content.isEmpty()) {
            json.addProperty("content", content);
        }
        Elements children = element.children();
        if (!children.isEmpty()) {
            JsonObject childJson = new JsonObject();
            for (Element child : children) {
                childJson.add(child.tagName(), elementToJson(child));
            }
            json.add("children", childJson);
        }

        return json;
    }
}


