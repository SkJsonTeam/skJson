package cz.coffee.skjson.api.nbts;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum NBTCustom {
    NBTTagEnd("tag end", NBTType.NBTTagEnd),
    NBTTagByte("byte", NBTType.NBTTagByte, Number.class),
    NBTTagShort("short", NBTType.NBTTagShort, Number.class),
    NBTTagInt("int", NBTType.NBTTagInt, Number.class),
    NBTTagLong("long", NBTType.NBTTagLong, Number.class),
    NBTTagFloat("float", NBTType.NBTTagFloat, Number.class),
    NBTTagDouble("double", NBTType.NBTTagDouble, Number.class),
    NBTTagString("string", NBTType.NBTTagString, String.class),
    NBTTagUUID("uuid", NBTType.NBTTagIntArray, String.class),
    NBTTagCompound("compound", NBTType.NBTTagCompound, NBTCompound.class),
    NBTTagByteArray("byte array", NBTType.NBTTagByteArray, Number[].class, true),
    NBTTagIntArray("int array", NBTType.NBTTagIntArray, Number[].class, true),
    NBTTagDoubleList("double list", NBTType.NBTTagList, Number[].class, true),
    NBTTagFloatList("float list", NBTType.NBTTagList, Number[].class, true),
    NBTTagLongList("long list", NBTType.NBTTagList, Number[].class, true),
    NBTTagIntList("int list", NBTType.NBTTagList, Number[].class, true),
    NBTTagCompoundList("compound list", NBTType.NBTTagList, NBTCompound[].class, true),
    NBTTagStringList("string list", NBTType.NBTTagList, String[].class, true);


    private static final Map<NBTType, NBTCustom> BY_TYPE = new HashMap<>();

    static {
        for (NBTCustom type : NBTCustom.values()) {
            BY_TYPE.put(type.nbtType, type);
        }
    }

    final String name;
    final NBTType nbtType;
    final Class<?> typeClass;
    final boolean isList;

    NBTCustom(String name, NBTType nbtType) {
        this(name, nbtType, Void.class);
    }

    NBTCustom(String name, NBTType nbtType, Class<?> typeClass) {
        this(name, nbtType, typeClass, false);
    }

    NBTCustom(String name, NBTType nbtType, Class<?> typeClass, boolean isList) {
        this.name = name + " tag";
        this.nbtType = nbtType;
        this.typeClass = typeClass;
        this.isList = isList;
    }

    @Nullable
    public static NBTCustom parseList(NBTCompound compound, String key) {
        if (compound == null) return null;
        NBTType nbtType = compound.getType(key);
        if (BY_TYPE.containsKey(nbtType)) {
            if (nbtType == NBTType.NBTTagList) {
                if (!compound.getIntegerList(key).isEmpty())
                    return NBTTagIntList;
                else if (!compound.getLongList(key).isEmpty())
                    return NBTTagLongList;
                else if (!compound.getFloatList(key).isEmpty())
                    return NBTTagFloatList;
                else if (!compound.getDoubleList(key).isEmpty())
                    return NBTTagDoubleList;
                else if (!compound.getCompoundList(key).isEmpty())
                    return NBTTagCompoundList;
                else if (!compound.getStringList(key).isEmpty())
                    return NBTTagStringList;
            }
            return BY_TYPE.get(nbtType);
        }
        return null;
    }

    public String getName() {
        return this.name;
    }
}
