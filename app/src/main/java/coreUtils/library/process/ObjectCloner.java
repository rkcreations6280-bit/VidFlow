package coreUtils.library.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A utility class providing a mechanism to perform deep copies of objects using Java Serialization.
 * <p>
 * This class cannot be instantiated. It provides a static method {@link #deepCopy(Serializable)}
 * to create a complete replica of an object graph, provided all objects in the graph implement
 * the {@link Serializable} interface.
 * </p>
 */
public final class ObjectCloner {

    /**
     * Logger used for recording errors and diagnostic information during the object cloning process.
     */
    private static final LoggerUtils logger = LoggerUtils.from(ObjectCloner.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ObjectCloner() {}

    /**
     * Creates a deep copy of the provided object using serialization.
     * <p>
     * This method serializes the input object into a byte array and then deserializes it
     * into a new instance, ensuring that all nested objects are also cloned.
     * </p>
     *
     * @param object the object to be cloned; must implement {@link Serializable}.
     * @param <T>    the type of the object being cloned.
     * @return a new instance of the object representing a deep copy,
     * or {@code null} if an error occurs during the process.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deepCopy(T object) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(object);
                oos.flush();
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            try (ObjectInputStream ois = new ObjectInputStream(bis)) {
                return (T) ois.readObject();
            }

        } catch (Exception error) {
            logger.error("Error while deep copying an object:", error);
            return null;
        }
    }
}