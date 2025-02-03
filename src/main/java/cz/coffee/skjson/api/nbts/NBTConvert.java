package cz.coffee.skjson.api.nbts;

import com.google.gson.*;
import cz.coffee.skjson.parser.ParserUtil;
import cz.coffee.skjson.utils.Logger;
import de.tr7zw.nbtapi.*;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class NBTConvert {

    public static JsonElement parse(String key, NBTCompound cmp) {
        try {
            NBTType type = cmp.getType(key);

            Logger.info(key + " -> " + type);
            Logger.info("cmp: -> %s", cmp.toString());

            if (type.equals(NBTType.NBTTagList)) {
                return parseList(key, cmp);
            } else {
                return parseNonList(key, cmp);
            }
        } catch (Exception ex) {
            Logger.error(ex);
            return JsonNull.INSTANCE;
        }
    }

    private static JsonElement parseList(String key, NBTCompound cmp) {
        NBTCustom type0 = NBTCustom.parseList(cmp, key);
        final JsonArray array = new JsonArray();

        switch (Objects.requireNonNull(type0)) {
            case NBTTagLongList:
                cmp.getLongList(key).forEach(array::add);
                break;
            case NBTTagCompoundList:
                for (ReadWriteNBT readWriteNBT : cmp.getCompoundList(key)) {
                    JsonObject obj = new JsonObject();
                    for (String readWriteNBTKey : readWriteNBT.getKeys()) {
                        JsonElement value = parse(readWriteNBTKey, (NBTCompound) readWriteNBT);
                        obj.add(readWriteNBTKey, value);
                    }
                    array.add(obj);
                }
                break;
            case NBTTagDoubleList:
                cmp.getDoubleList(key).forEach(array::add);
                break;
            case NBTTagFloatList:
                cmp.getFloatList(key).forEach(array::add);
                break;
            case NBTTagIntList:
                cmp.getIntegerList(key).forEach(array::add);
                break;
            case NBTTagStringList:
                cmp.getStringList(key).forEach(array::add);
                break;
            default:
                return JsonNull.INSTANCE;
        }
        return array;
    }

    private static JsonElement parseNonList(String key, NBTCompound cmp) {
        NBTCustom type0 = NBTCustom.valueOf(cmp.getType(key).toString());

        return switch (type0) {
            case NBTTagByte -> {
                Byte value = cmp.getByte(key);
                yield value == 1 || value == 0 ? new JsonPrimitive(value == 1) : ParserUtil.parse(value);
            }
            case NBTTagShort -> ParserUtil.parse(cmp.getShort(key));
            case NBTTagInt -> ParserUtil.parse(cmp.getInteger(key));
            case NBTTagLong -> ParserUtil.parse(cmp.getLong(key));
            case NBTTagFloat -> ParserUtil.parse(cmp.getFloat(key));
            case NBTTagDouble -> ParserUtil.parse(cmp.getDouble(key));
            case NBTTagCompound -> parseCompound(Objects.requireNonNull(cmp.getCompound(key)));
            case NBTTagString -> new Gson().toJsonTree(cmp.getString(key));
            case NBTTagByteArray -> parseByteArray(Objects.requireNonNull(cmp.getByteArray(key)));
            case NBTTagIntArray -> parseIntArray(Objects.requireNonNull(cmp.getIntArray(key)));
            default -> JsonNull.INSTANCE;
        };
    }

    private static JsonObject parseCompound(NBTCompound compound) {
        JsonObject object = new JsonObject();
        for (String key : compound.getKeys()) {
            JsonElement value = parse(key, compound);
            object.add(key, value);
        }
        return object;
    }

    private static JsonArray parseByteArray(byte[] array) {
        JsonArray jsonArray = new JsonArray();
        for (byte b : array) {
            jsonArray.add(b);
        }
        return jsonArray;
    }

    private static JsonArray parseIntArray(int[] array) {
        JsonArray jsonArray = new JsonArray();
        for (int i : array) {
            jsonArray.add(i);
        }
        return jsonArray;
    }

    static void setValue(String key, JsonElement value, NBTCompound cont) {
        Object data = ParserUtil.jsonToType(value);

        if (data instanceof String) {
            cont.setString(key, (String) data);
        } else if (data instanceof Byte) {
            cont.setByte(key, (Byte) data);
        } else if (data instanceof Integer) {
            cont.setInteger(key, (Integer) data);
        } else if (data instanceof Double) {
            cont.setDouble(key, (Double) data);
        } else if (data instanceof Float) {
            cont.setFloat(key, (Float) data);
        } else if (data instanceof Long) {
            cont.setLong(key, (Long) data);
        } else if (data instanceof Boolean) {
            cont.setBoolean(key, (Boolean) data);
        }
    }

    static void processList(NBTCompound main, JsonArray array, String mainKey) {
        if (array.isEmpty() || main == null) return;

        Object single = ParserUtil.jsonToType(array.get(0));

        try {
            if (single instanceof JsonObject) {
                processCompoundList(main, array, mainKey);
            } else if (single instanceof Float) {
                processTypedList(main.getFloatList(mainKey), array, Float.class);
            } else if (single instanceof Double) {
                processTypedList(main.getDoubleList(mainKey), array, Double.class);
            } else if (single instanceof Long) {
                processTypedList(main.getLongList(mainKey), array, Long.class);
            } else if (single instanceof Integer) {
                processTypedList(main.getIntegerList(mainKey), array, Integer.class);
            } else if (single instanceof Byte) {
                processByteArray(main.getByteArray(mainKey), array);
            } else if (single instanceof String) {
                processTypedList(main.getStringList(mainKey), array, String.class);
            } else if (single instanceof Boolean) {
                processBooleanArray(main.getByteArray(mainKey), array);
            }
        } catch (Exception ex) {
            Logger.error(ex);
        }
    }

    private static void processCompoundList(NBTCompound main, JsonArray array, String mainKey) {
        NBTCompoundList inList = main.getCompoundList(mainKey);
        inList.clear();
        for (JsonElement element : array) {
            inList.addCompound(processJson(element.getAsJsonObject()));
        }
    }

    private static <T> void processTypedList(NBTList<T> inList, JsonArray array, Class<T> clazz) {
        inList.clear();
        for (JsonElement element : array) {
            T data = clazz.cast(ParserUtil.jsonToType(element));
            inList.add(data);
        }
    }

    private static void processByteArray(byte[] inList, JsonArray array) {
        for (int i = 0; i < array.size(); i++) {
            Byte data = ParserUtil.jsonToType(array.get(i));
            inList[i] = data;
        }
    }

    private static void processBooleanArray(byte[] inList, JsonArray array) {
        for (int i = 0; i < array.size(); i++) {
            Boolean data = ParserUtil.jsonToType(array.get(i));
            inList[i] = (byte) (data ? 1 : 0);
        }
    }

    static void processObject(NBTCompound main, JsonObject object, String mainKey) {
        if (main == null) return;

        main.addCompound(mainKey);
        for (String key : object.keySet()) {
            JsonElement unparsed = object.get(key);
            if (unparsed.isJsonPrimitive()) {
                setValue(key, unparsed, main.getCompound(mainKey));
            } else if (unparsed.isJsonObject()) {
                processObject(Objects.requireNonNull(main.getCompound(mainKey)).getCompound(key), unparsed.getAsJsonObject(), key);
            } else if (unparsed.isJsonArray()) {
                processList(main.getCompound(mainKey), unparsed.getAsJsonArray(), key);
            }
        }
    }

    static NBTContainer processJson(JsonObject e) {
        NBTContainer container = new NBTContainer();
        Deque<JsonElement> elements = new ArrayDeque<>();
        elements.add(e);

        while (!elements.isEmpty()) {
            JsonElement element = elements.pollFirst();
            if (element instanceof JsonObject compound) {
                processCompoundElements(container, compound);
            } else if (element instanceof JsonArray list) {
                processArrayElements(container, list);
            }
        }
        return container;
    }

    private static void processCompoundElements(NBTContainer container, JsonObject compound) {
        for (var entry : compound.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                setValue(key, value, container);
            } else if (value.isJsonObject()) {
                processObject(container, value.getAsJsonObject(), key);
            } else if (value.isJsonArray()) {
                processList(container, value.getAsJsonArray(), key);
            }
        }
    }

    private static void processArrayElements(NBTContainer container, JsonArray list) {
        for (int i = 0; i < list.size(); i++) {
            JsonElement value = list.get(i);
            if (value.isJsonObject()) {
                processObject(container, value.getAsJsonObject(), String.valueOf(i));
            } else if (value.isJsonArray()) {
                processList(container, value.getAsJsonArray(), String.valueOf(i));
            }
        }
    }

    public static ItemStack parseFromJson(ItemStack item, JsonObject tags, boolean mainComponents) {
        NBTItem nbtItem = new NBTItem(item, mainComponents);
        NBTContainer nbtContainer = processJson(tags);
        nbtItem.mergeCompound(nbtContainer);
        return nbtItem.getItem();

    }
}
