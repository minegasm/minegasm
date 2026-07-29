package net.minegasm.config;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gson factory that (de)serializes the immutable config value types ({@link ConfigValue}) through
 * their single all-args constructor instead of by reflective field assignment.
 *
 * <p>Two reasons this exists. First, the config types are immutable with final fields; building each
 * one through its constructor runs the same validation and defaulting that a fresh instance gets, so
 * a partial or older file loads to a fully normalised object rather than one with raw or null fields.
 * Second, Minecraft 1.19.2 ships Gson 2.8.9, whose default handling tries to {@code Field.set} final
 * fields and can fail on that line; constructing an object never sets a final field after the fact, so
 * this path is correct on every Gson version. It is a library concern, not a Minecraft one, so it is
 * registered unconditionally (the core stays guard-free) and every variant's test run exercises it.
 *
 * <p>JSON member names are the field names, written in declaration order, and each field delegates to
 * {@code gson.getAdapter(...)}, so output stays byte-identical to the previous record encoding and
 * existing on-disk configs remain readable.
 */
final class ConfigValueTypeAdapterFactory implements TypeAdapterFactory {

    static final ConfigValueTypeAdapterFactory INSTANCE = new ConfigValueTypeAdapterFactory();

    private ConfigValueTypeAdapterFactory() {
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        if (raw.isInterface() || !ConfigValue.class.isAssignableFrom(raw)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<T> valueType = (Class<T>) raw;
        return new ValueAdapter<>(gson, valueType);
    }

    private static final class ValueAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final Class<T> valueType;
        private final Field[] fields;
        private final Constructor<T> constructor;
        private final Map<String, Integer> indexByName;

        ValueAdapter(Gson gson, Class<T> valueType) {
            this.gson = gson;
            this.valueType = valueType;
            // Instance fields in declaration order; the all-args constructor takes them in the same
            // order. Static fields (constants, defaults) are not part of the serialised state.
            List<Field> instanceFields = new ArrayList<>();
            for (Field f : valueType.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    instanceFields.add(f);
                }
            }
            this.fields = instanceFields.toArray(new Field[0]);
            Class<?>[] paramTypes = new Class<?>[fields.length];
            this.indexByName = new HashMap<>();
            for (int i = 0; i < fields.length; i++) {
                paramTypes[i] = fields[i].getType();
                indexByName.put(fields[i].getName(), i);
            }
            try {
                this.constructor = valueType.getDeclaredConstructor(paramTypes);
                this.constructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "no all-args constructor for config value " + valueType, e);
            }
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            for (Field field : fields) {
                out.name(field.getName());
                Object fieldValue;
                try {
                    fieldValue = field.get(value);
                } catch (IllegalAccessException e) {
                    throw new IOException("failed reading config field "
                            + valueType.getSimpleName() + "." + field.getName(), e);
                }
                @SuppressWarnings("unchecked")
                TypeAdapter<Object> adapter = (TypeAdapter<Object>) gson.getAdapter(
                        TypeToken.get(field.getGenericType()));
                adapter.write(out, fieldValue);
            }
            out.endObject();
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            Object[] args = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                // Absent members: primitives need their zero value, references stay null.
                args[i] = defaultValue(fields[i].getType());
            }
            in.beginObject();
            while (in.hasNext()) {
                Integer index = indexByName.get(in.nextName());
                if (index == null) {
                    in.skipValue(); // unknown members are tolerated, as with Gson's field binding
                    continue;
                }
                args[index] = gson.getAdapter(
                        TypeToken.get(fields[index].getGenericType())).read(in);
            }
            in.endObject();
            try {
                return constructor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IOException("failed constructing config value "
                        + valueType.getSimpleName(), e);
            }
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0f;
            }
            return 0d; // double.class, the only remaining primitive
        }
    }
}
