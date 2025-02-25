package cz.coffeerequired.modules;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.registrations.EventValues;
import ch.njol.skript.util.Getter;
import ch.njol.skript.util.Version;
import ch.njol.util.coll.CollectionUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import cz.coffeerequired.SkJson;
import cz.coffeerequired.api.Extensible;
import cz.coffeerequired.api.Register;
import cz.coffeerequired.api.annotators.Module;
import cz.coffeerequired.api.json.*;
import cz.coffeerequired.skript.core.bukkit.JSONFileWatcherSave;
import cz.coffeerequired.skript.core.conditions.*;
import cz.coffeerequired.skript.core.effects.*;
import cz.coffeerequired.skript.core.events.WatcherEvent;
import cz.coffeerequired.skript.core.expressions.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.converter.Converters;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

import static cz.coffeerequired.skript.core.SupportSkriptJson.JsonLoopExpression;
import static cz.coffeerequired.skript.core.SupportSkriptJson.JsonSupportElement;

@Module(module = "core")
public class Core extends Extensible {

    static final Collection<Class<?>> allowedTypes = List.of(
            ItemStack.class,
            Location.class,
            World.class,
            Chunk.class,
            Inventory.class,
            ConfigurationSerializable.class
    );

    public Core() {
        this.sign = this.getClass().getSimpleName();
        this.skriptElementPath = "cz.coffeerequired.skript.core";
    }

    public void tryRegisterDefaultConverters() {
        try {
            allowedTypes.forEach(type -> Converters.registerConverter(JsonElement.class, type, GsonParser::fromJson));
        } catch (Exception e) {
            SkJson.exception(e, "Error while registering default converters: %s", e.getMessage());
        }
    }

    @Override
    public void registerElements(Register.SkriptRegister register) {
        register.apply(this);

        this.tryRegisterDefaultConverters();

        register.registerType(new ClassInfo<>(JsonElement.class, "jsonelement")
                        .user("jsonelement")
                        .name("jsonelement")
                        .description("Json representation of any object in skript")
                        .since("2.9, 4.1 - change")
                        .parser(new JSONTypeParser())
                        .serializer(new JSONTypeSerializer())
                        .changer(new JSONTypeDefaultChanger()),
                "type.json"
        );

        register.registerType(new ClassInfo<>(JsonPath.class, "jsonpath")
                        .user("jsonpath")
                        .name("json path")
                        .description("Json path representation")
                        .since("4.1 - API UPDATE")
                        .parser(JsonPath.parser)
                        .serializer(JsonPath.serializer)
                        .changer(new Changer<>() {
                            @Override
                            public @Nullable Class<?>[] acceptChange(ChangeMode changeMode) {
                                if (changeMode == ChangeMode.ADD) {
                                    return CollectionUtils.array(Object.class, Object[].class);
                                }
                                return null;
                            }

                            @Override
                            public void change(JsonPath[] what, @Nullable Object[] delta, ChangeMode changeMode) {
                                if (changeMode == ChangeMode.ADD) {
                                    if (delta == null || delta.length < 1) {
                                        SkJson.warning("Module [Core]: delta need to be defined");
                                        return;
                                    }

                                    JsonPath path = what[0];
                                    if (path == null) {
                                        SkJson.warning("Module [Core]: json path is null");
                                        return;
                                    }

                                    SerializedJson serializedJson = new SerializedJson(path.getInput());
                                    var converted = Arrays.stream(delta).filter(Objects::nonNull).map(GsonParser::toJson).toArray(JsonElement[]::new);

                                    IntStream.range(0, converted.length).forEach(idx -> {
                                        var json = converted[idx];
                                        var result = serializedJson.searcher.keyOrIndex(path.getKeys());
                                        if (result == null) {
                                            SkJson.severe("Module [Core]: result need to be defined");
                                            return;
                                        }
                                        if (!(result instanceof JsonArray)) {
                                            SkJson.severe("Module [Core]: additional can be used only for JSON arrays. | JSON array given " + result.getClass().getSimpleName());
                                            return;
                                        }
                                        var keys = path.getKeys();
                                        var key = Map.entry((((JsonArray) result).size()) + idx + "", SkriptJsonInputParser.Type.Index);
                                        keys.add(key);
                                        serializedJson.changer.value(keys, json);
                                    });
                                }
                            }
                        }),
                "type.jsonpath"
        );

        register.registerExpression(ExprNewJson.class, JsonElement.class, ExpressionType.SIMPLE,
                "json from file %strings%",
                "json from website %strings%",
                "json (from|of) %objects%"
        );
        register.registerExpression(ExprPrettyPrint.class, String.class, ExpressionType.SIMPLE,
                "%jsonelement% as pretty[ printed]",
                "%jsonelement% as uncolo[u]red pretty[ printed]"
        );

        register.registerEffect(EffNewFile.class, "new json file %~string%", "new json file %~string% with [content] %-objects%");
        register.registerExpression(ExprJsonPath.class, JsonPath.class, ExpressionType.SIMPLE,
                "json path %string% in %jsonelement%"
        );
        register.registerExpression(ExprChanger.class, Object.class, ExpressionType.SIMPLE,
                "(1:value|0:key) of %jsonpath%"
        );

        register.registerExpression(ExprStrictLiteralJson.class, Object.class, ExpressionType.PATTERN_MATCHES_EVERYTHING,
                "%jsonelement%.<([\\p{L}\\d_%\\[\\]*]+|\"[^\"]*\")(\\\\[\\\\]|\\\\[\\\\d+\\\\])?(\\\\.)?>"
        );

        register.registerCondition(CondJsonHas.class,
                "%jsonelement% has [:all] (:value[s]|:key[s]) %objects%",
                "%jsonelement% does(n't| not) have [:all] (:value[s]|:key[s]) %objects%"
        );
        register.registerCondition(CondJsonType.class,
                "type of %jsonelement% is (json[-]:object|json[-]:array|json[-]:primitive|json[-]:null)",
                "type of %jsonelement% (is(n't| not)) (json[-]:object|json[-]:array|json[-]:primitive|json[-]:null)"
        );
        register.registerExpression(JsonSupportElement.class, Object.class, ExpressionType.COMBINED,
                "(1st|first) (:value|:key) of %jsonelement%",
                "(2nd|second) (:value|:key) of %jsonelement%",
                "(3rd|third) (:value|:key) of %jsonelement%",
                "last (:value|:key) of %jsonelement%",
                "random (:value|:key) of %jsonelement%",
                "%integer%. (:value|:key) of %jsonelement%"
        );
        register.registerExpression(ExprGetAllKeys.class, String.class, ExpressionType.SIMPLE, "[all] keys [%-string%] of %jsonelement%");
        register.registerExpression(JsonLoopExpression.class, Object.class, ExpressionType.SIMPLE, "[the] json-(:key|:value)[-<(\\d+)>]");
        register.registerExpression(ExprCountElements.class, Integer.class, ExpressionType.SIMPLE, "[the] count of (:key[s]|:value[s]) %object% in %jsonelement%");
        register.registerExpression(ExprJsonElements.class, Object.class, ExpressionType.COMBINED, "(element|value) [%-string%] of %jsonelement%", "(elements|values) [%-string%] of %jsonelement%");
        register.registerEffect(EffMapJson.class, "[:async] (map|copy) %jsonelement% to %objects%");
        register.registerPropertyExpression(ExprFormattingJsonToVariable.class, JsonElement.class, "form[atted [json]]", "jsonelements");
        register.registerSimplePropertyExpression(ExprJsonSize.class, Integer.class, "json size", "jsonelements");
        register.registerExpression(ExprAllJsonFiles.class, String.class, ExpressionType.COMBINED, "[all] json [files] (from|in) (dir[ectory]|folder) %string%");
        register.registerEffect(EffNewFile.class,
                "create json file %string% [:with configuration<\\[\\s*((\\w+)=([\\w-]+)(?:,\\s*)?)+\\s*\\]>]",
                "create json file %string% and write to it %object% [:with configuration<\\[\\s*((\\w+)=([\\w-]+)(?:,\\s*)?)+\\s*\\]>]"
        );
        register.registerCondition(CondJsonFileExist.class, "json file %string% exists", "json file %string% does(n't| not) exist");
        register.registerCondition(CondJsonIsEmpty.class, "json %jsonelement% is empty", "json %jsonelement% is(n't| not) empty");

        /*
            ################ CACHE ############################
         */
        register.registerEffect(AEffHandleWatcher.class, "bind storage watcher to %string%", "unbind storage watcher from %string%");
        register.registerCondition(CondIsListened.class, "json storage %string% is listen", "json storage %string% is(n't| not) listen");
        register.registerEffect(EffVirtualStorage.class, "create json virtual storage named %string%");
        register.registerEffect(AEffBindFile.class, "bind json file %string% as %string%", "bind json file %string% as %string% and let bind storage watcher");
        register.registerExpression(ExprGetCacheStorage.class, JsonElement.class, ExpressionType.SIMPLE, "json storage of id %string%", "all json storages");
        register.registerEffect(AEffUnbindFile.class, "un(bind|link) json storage [id] %string%");
        register.registerEffect(AEffSaveStorage.class, "save json storage [id] %string%", "save all json storages");
        register.registerCondition(CondIsCached.class, "json storage of [id] %string% is cached", "json storage of [id] %string% is(n't| not) cached");

        register.registerEvent(
                "*Json watcher save", WatcherEvent.class, JSONFileWatcherSave.class,
                "will only run when the json watcher notices a change in the file",
                "on json watcher save",
                "2.9",
                "[json-] watcher save"
        );

        if (Skript.getVersion().isSmallerThan(new Version(2, 10, 0))) {
            EventValues.registerEventValue(JSONFileWatcherSave.class, JsonElement.class,
                    new Getter<>() {
                        @Override
                        public JsonElement get(JSONFileWatcherSave event) {
                            return event.getJson();
                        }
                    }, 0);
            EventValues.registerEventValue(JSONFileWatcherSave.class, UUID.class,
                    new Getter<>() {
                        @Override
                        public UUID get(JSONFileWatcherSave event) {
                            return event.getUuid();
                        }
                    }, 0);
            EventValues.registerEventValue(JSONFileWatcherSave.class, File.class,
                    new Getter<>() {
                        @Override
                        public File get(JSONFileWatcherSave event) {
                            return event.getLinkedFile();
                        }
                    }, 0);
        }
    }
}
