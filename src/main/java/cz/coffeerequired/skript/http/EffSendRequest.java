package cz.coffeerequired.skript.http;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import cz.coffeerequired.SkJson;
import cz.coffeerequired.api.http.MimeMultipartData;
import cz.coffeerequired.api.http.RequestClient;
import cz.coffeerequired.api.http.RequestResponse;
import cz.coffeerequired.api.requests.Request;
import cz.coffeerequired.api.requests.RequestMethod;
import cz.coffeerequired.api.requests.RequestStatus;
import cz.coffeerequired.api.requests.Response;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ch.njol.skript.util.LiteralUtils.canInitSafely;
import static ch.njol.skript.util.LiteralUtils.defendExpression;
import static cz.coffeerequired.SkJson.logger;

@Name("Send created/prepared request")
@Examples("send prepared {_request}")
@Description("Send prepared/created request to the given method and uri")
@Since("2.9.9-pre Api Changes")
@ApiStatus.Experimental
public class EffSendRequest extends Effect {

    private static final Field DELAYED;
    private static final ExecutorService threadPool =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    static {
        Field _DELAYED = null;
        try {
            _DELAYED = Delay.class.getDeclaredField("DELAYED");
            _DELAYED.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Skript.error("Skript's 'delayed' method could not be resolved. Some Skript warnings may not be available.");
        }
        DELAYED = _DELAYED;
    }

    Expression<Request> exprRequest;
    boolean sync;

    @Override
    protected void execute(@NotNull Event event) {
        var request = exprRequest.getSingle(event);
        assert request != null;
        if (sync) {
            var response = sendRequest(request);
            assert response != null;
            var rsp = new Response(response.getStatusCode(), response.getBodyContent(true), response.getResponseHeader().toJson());
            request.setResponse(rsp);
            return;
        }
        var vars = Variables.copyLocalVariables(event);
        CompletableFuture.supplyAsync(() -> sendRequest(request), threadPool)
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        logger().error(Objects.requireNonNull(getParser().getNode()).toString());
                        request.setResponse(Response.empty());
                        return;
                    }
                    if (resp != null) {
                        Bukkit.getScheduler().runTask(SkJson.getInstance(), () -> {
                            var rsp = new Response(resp.getStatusCode(), resp.getBodyContent(true), resp.getResponseHeader().toJson());
                            request.setResponse(rsp);
                            Variables.setLocalVariables(event, vars);
                            if (getNext() != null) TriggerItem.walk(getNext(), event);
                        });
                    }
                });
    }

    @Override
    protected TriggerItem walk(@NotNull Event e) {
        var rq = this.exprRequest.getSingle(e);
        if (rq == null) return null;
        debug(e, true);
        if (!sync) delay(e);
        if (!sync) execute(e);
        if (sync) return super.walk(e);
        return null;
    }

    private RequestResponse sendRequest(Request request) {
        URI URL = null;
        try {
            StringBuilder uriBuilder = new StringBuilder(request.getUri().toString());
            if (!request.getQueryParams().isEmpty()) {
                uriBuilder.append("?");
                request.getQueryParams().forEach((key, value) -> {
                    try {
                        uriBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                                .append("=")
                                .append(URLEncoder.encode(Arrays.toString(value), StandardCharsets.UTF_8))
                                .append("&");
                    } catch (Exception e) {
                        throw new RuntimeException("Error encoding query parameters", e);
                    }
                });
                uriBuilder.setLength(uriBuilder.length() - 1);
            }

            URL = URI.create(uriBuilder.toString()).normalize();
        } catch (Exception ex) {
            logger().exception(Objects.requireNonNull(getParser().getNode()).toString(), ex);
        }

        try (RequestClient client = new RequestClient()) {
            client.setUri((URL != null ? URL : request.getUri()).toString().replaceAll("§", "&"));
            RequestResponse rsp;
            final boolean hasAttachments = !request.getAttachments().isEmpty();
            if (hasAttachments) {
                client.setAttachments(request.getAttachments());

                var mpd = MimeMultipartData.newBuilder().addContent(request.getContent());
                request.getAttachments().forEach(attachment ->
                        mpd.addFile(attachment.path(), attachment.file().toPath(), MimeMultipartData.FileType.AUTOMATIC)
                );

                if (!request.getMethod().equals(RequestMethod.GET)) {
                     client.setBodyPublisher(mpd.build().getBodyPublisher());
                } else {
                    logger().warning("Sending request to " + URL + " with method GET, method GET doesn't support body!");
                }

                rsp = RequestResponse.of(client
                        .method(request.getMethod())
                        .addHeader(request.getHeader())
                        .sendAsync()
                        .get());
            } else {
                if (!request.getMethod().equals(RequestMethod.GET)) {
                   client.setJsonBody(request.getContent());
                } else {
                    logger().warning("Sending request to " + URL + " with method GET, method GET doesn't support body!");
                }

                rsp = RequestResponse.of(client.method(request.getMethod())
                        .addHeader(request.getHeader())
                        .sendAsync()
                        .get());
            }

            request.setStatus(rsp.isSuccessful() ? RequestStatus.OK : RequestStatus.FAILED);
            return rsp;
        } catch (IOException | ExecutionException | InterruptedException e) {
            var ex = new RuntimeException(e);
            logger().exception(Objects.requireNonNull(getParser().getNode()).toString(), ex);
            return null;
        }
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        assert event != null;
        return "execute prepared " + this.exprRequest.toString(event, debug);
    }

    @SuppressWarnings("unchecked")
    private void delay(Event e) {
        if (DELAYED != null) {
            try {
                ((Set<Event>) DELAYED.get(null)).add(e);
            } catch (IllegalAccessException illegalAccessException) {
                logger().exception(Objects.requireNonNull(getParser().getNode()).toString(), illegalAccessException);
            }
        }
    }

    @Override
    public boolean init(Expression<?> @NotNull [] expressions, int matchedPattern, @NotNull Kleenean isDelayed, SkriptParser.@NotNull ParseResult parseResult) {
        exprRequest = defendExpression(expressions[0]);
        sync = parseResult.hasTag("sync");
        return canInitSafely(exprRequest);
    }
}
