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
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * Gson factory that (de)serializes Java records through their canonical constructor instead of
 * reflective field assignment.
 *
 * <p>Gson gained native record support in 2.10.0. Minecraft 1.19.2 ships Gson 2.8.9, which has none:
 * it tries to {@code Field.set} a record's final fields and throws
 * {@code IllegalAccessException: Can not set final ... field} (surfacing as an {@code AssertionError}),
 * failing every config load on that line. The rest of the supported lines ship Gson 2.10+, so this gap
 * exists only on 1.19.2 — but building a record via its constructor never <em>sets</em> a final field,
 * so registering this makes record handling correct on every Gson version. It is a library-version
 * concern, not a Minecraft-version one, so it is registered unconditionally (the pure core stays
 * guard-free) and every variant's test run exercises it.
 *
 * <p>JSON member names are the record component names and components are written in declaration order,
 * matching Gson's own record encoding, and each component delegates to {@code gson.getAdapter(...)} — so
 * output is byte-identical to the native-record path and existing on-disk configs remain readable.
 */
final class RecordTypeAdapterFactory implements TypeAdapterFactory {

    static final RecordTypeAdapterFactory INSTANCE = new RecordTypeAdapterFactory();

    private RecordTypeAdapterFactory() {
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        if (!raw.isRecord()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<T> recordType = (Class<T>) raw;
        return new RecordAdapter<>(gson, recordType);
    }

    private static final class RecordAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final Class<T> recordType;
        private final RecordComponent[] components;
        private final Constructor<T> canonical;
        private final Map<String, Integer> indexByName;

        RecordAdapter(Gson gson, Class<T> recordType) {
            this.gson = gson;
            this.recordType = recordType;
            this.components = recordType.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[components.length];
            this.indexByName = new HashMap<>();
            for (int i = 0; i < components.length; i++) {
                paramTypes[i] = components[i].getType();
                indexByName.put(components[i].getName(), i);
            }
            try {
                this.canonical = recordType.getDeclaredConstructor(paramTypes);
                this.canonical.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("no canonical constructor for record " + recordType, e);
            }
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            for (RecordComponent component : components) {
                out.name(component.getName());
                Object componentValue;
                try {
                    componentValue = component.getAccessor().invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new IOException("failed reading record component "
                            + recordType.getSimpleName() + "." + component.getName(), e);
                }
                @SuppressWarnings("unchecked")
                TypeAdapter<Object> adapter = (TypeAdapter<Object>) gson.getAdapter(
                        TypeToken.get(component.getGenericType()));
                adapter.write(out, componentValue);
            }
            out.endObject();
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                // Absent members: primitives need their zero value, references stay null.
                args[i] = defaultValue(components[i].getType());
            }
            in.beginObject();
            while (in.hasNext()) {
                Integer index = indexByName.get(in.nextName());
                if (index == null) {
                    in.skipValue(); // unknown members are tolerated, as with Gson's field binding
                    continue;
                }
                args[index] = gson.getAdapter(
                        TypeToken.get(components[index].getGenericType())).read(in);
            }
            in.endObject();
            try {
                return canonical.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IOException("failed constructing record " + recordType.getSimpleName(), e);
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
            return 0d; // double.class — the only remaining primitive
        }
    }
}
