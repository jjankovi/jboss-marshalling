/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.marshalling;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.ServiceLoader;

import org.jboss.marshalling.util.CustomServiceLoader;

/**
 * Static utility methods for simplifying use of marshallers.
 * @apiviz.landmark
 */
public final class Marshalling {

    private Marshalling() {
    }

    /**
     * Get a marshaller factory, by name.  Uses the thread's current context classloader, if available, to locate
     * the factory.
     *
     * @param name the name of the protocol to acquire
     * @return the marshaller factory, or {@code null} if no matching factory was found
     *
     * @see ServiceLoader
     *
     * @deprecated It is recommended that you use {@link #getProvidedMarshallerFactory(String)} instead; using the context
     * class loader to find a marshalling implementation is risky at best as the user may have just about anything on their
     * class path.
     */
    @Deprecated
    public static MarshallerFactory getMarshallerFactory(String name) {
        return loadMarshallerFactory(CustomServiceLoader.load(ProviderDescriptor.class), name);
    }

    /**
     * Get a marshaller factory, by name.  Uses the given classloader to locate
     * the factory.
     *
     * @param name the name of the protocol to acquire
     * @param classLoader the class loader to use
     * @return the marshaller factory, or {@code null} if no matching factory was found
     *
     * @see ServiceLoader
     */
    public static MarshallerFactory getMarshallerFactory(String name, ClassLoader classLoader) {
        return loadMarshallerFactory(CustomServiceLoader.load(ProviderDescriptor.class, classLoader), name);
    }

    /**
     * Get a marshaller factory which is visible to this implementation, by name.  Uses the class loader of this API.
     *
     * @param name the name of the protocol to acquire
     * @return the marshaller factory, or {@code null} if no matching factory was found
     */
    public static MarshallerFactory getProvidedMarshallerFactory(String name) {
        return loadMarshallerFactory(CustomServiceLoader.load(ProviderDescriptor.class, Marshalling.class.getClassLoader()), name);
    }

    private static MarshallerFactory loadMarshallerFactory(CustomServiceLoader<ProviderDescriptor> loader, String name) {
        for (ProviderDescriptor descriptor : loader) {
            if (name.equals(descriptor.getName())) {
                return descriptor.getMarshallerFactory();
            }
        }
        return null;
    }

    private static final StreamHeader NULL_STREAM_HEADER = new StreamHeader() {
        public void readHeader(final ByteInput input) throws IOException {
        }

        public void writeHeader(final ByteOutput output) throws IOException {
        }

        public String toString() {
            return "Null StreamHeader";
        }
    };

    /**
     * Get the default stream header producer, which reads and writes no header at all.
     *
     * @return the default stream header producer
     */
    public static StreamHeader nullStreamHeader() {
        return NULL_STREAM_HEADER;
    }

    /**
     * Create a stream header that uses the given bytes.
     *
     * @param headerBytes the header bytes
     * @return the stream header object
     */
    public static StreamHeader streamHeader(final byte[] headerBytes) {
        return new StaticStreamHeader(headerBytes);
    }

    private static final class StaticStreamHeader implements StreamHeader, Serializable {
        private final byte[] headerBytes;

        private static final long serialVersionUID = 8465784729867667872L;

        public StaticStreamHeader(final byte[] bytes) {
            headerBytes = bytes;
        }

        public void readHeader(final ByteInput input) throws IOException {
            final byte[] buf = new byte[headerBytes.length];
            readFully(input, buf);
            if (! Arrays.equals(buf, headerBytes)) {
                throw new StreamCorruptedException("Header is incorrect (expected " + Arrays.toString(headerBytes) + ", got " + Arrays.toString(buf) + ")");
            }
        }

        public void writeHeader(final ByteOutput output) throws IOException {
            output.write(headerBytes);
        }

        public String toString() {
            return "static StreamHeader@" + Integer.toHexString(hashCode()) + " (" + headerBytes.length + " bytes)";
        }
    }

    /**
     * Read bytes from a {@code ByteInput}.  Fully fills in the array.
     *
     * @param input the input
     * @param dest the destination
     * @throws EOFException if the end of file is reached before the array is filled
     * @throws IOException if an I/O error occurs
     */
    public static void readFully(ByteInput input, byte[] dest) throws IOException {
        readFully(input, dest, 0, dest.length);
    }

    /**
     * Read bytes from a {@code ByteInput}.  Fully fills in {@code len} bytes in the array.
     *
     * @param input the input
     * @param dest the destination
     * @param offs the offset into the array
     * @param len the number of bytes
     * @throws EOFException if the end of file is reached before the array is filled
     * @throws IOException if an I/O error occurs
     */
    public static void readFully(ByteInput input, byte[] dest, int offs, int len) throws IOException {
        while (len > 0) {
            final int r = input.read(dest, offs, len);
            if (r == -1) {
                throw new EOFException();
            }
            len -= r;
            offs += r;
        }
    }

    /**
     * Create a {@code ByteInput} wrapper for a {@code ByteBuffer}.
     *
     * @param buffer the byte buffer
     * @return the byte input wrapper
     */
    public static ByteInput createByteInput(final ByteBuffer buffer) {
        return new ByteBufferInput(buffer);
    }

    /**
     * Create a {@code ByteInput} wrapper for an {@code InputStream}.
     *
     * @param inputStream the input stream
     * @return the byte input wrapper
     */
    public static ByteInput createByteInput(final InputStream inputStream) {
        return inputStream instanceof ByteInput ? (ByteInput) inputStream : new InputStreamByteInput(inputStream);
    }

    /**
     * Create an {@code InputStream} wrapper for a {@code ByteInput}.
     *
     * @param byteInput the byte input
     * @return the input stream wrapper
     */
    public static InputStream createInputStream(final ByteInput byteInput) {
        return byteInput instanceof InputStream ? (InputStream) byteInput : new ByteInputStream(byteInput);
    }

    /**
     * Create a {@code ByteOutput} wrapper for a {@code ByteBuffer}.
     *
     * @param buffer the byte buffer
     * @return the byte output wrapper
     */
    public static ByteOutput createByteOutput(final ByteBuffer buffer) {
        return new ByteBufferOutput(buffer);
    }

    /**
     * Create a {@code ByteOutput} wrapper for an {@code OutputStream}.
     *
     * @param outputStream the output stream
     * @return the byte output wrapper
     */
    public static ByteOutput createByteOutput(final OutputStream outputStream) {
        return outputStream instanceof ByteOutput ? (ByteOutput) outputStream : new OutputStreamByteOutput(outputStream);
    }

    /**
     * Create a {@code OutputStream} wrapper for a {@code ByteOutput}.
     *
     * @param byteOutput the byte output
     * @return the output stream wrapper
     */
    public static OutputStream createOutputStream(final ByteOutput byteOutput) {
        return byteOutput instanceof OutputStream ? (OutputStream)byteOutput : new ByteOutputStream(byteOutput);
    }

    private static final ClassExternalizerFactory NULL_CLASS_EXTERNALIZER_FACTORY = new ClassExternalizerFactory() {
        public Externalizer getExternalizer(final Class<?> type) {
            return null;
        }

        public String toString() {
            return "Null class externalizer factory";
        }
    };

    /**
     * Return the null class externalizer factory.  This instance does not externalize any classes.
     *
     * @return the null class externalizer factory
     */
    public static ClassExternalizerFactory nullClassExternalizerFactory() {
        return NULL_CLASS_EXTERNALIZER_FACTORY;
    }

    private static final ObjectResolver NULL_OBJECT_RESOLVER = new ObjectResolver() {
        public Object readResolve(final Object replacement) {
            return replacement;
        }

        public Object writeReplace(final Object original) {
            return original;
        }

        public String toString() {
            return "Null object resolver";
        }
    };

    /**
     * Return the null object resolver.  This instance does not translate objects in any way.
     *
     * @return the null object resolver
     */
    public static ObjectResolver nullObjectResolver() {
        return NULL_OBJECT_RESOLVER;
    }

    private static final ObjectTable NULL_OBJECT_TABLE = new ObjectTable() {
        public Writer getObjectWriter(final Object object) {
            return null;
        }

        public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
            return null;
        }

        public String toString() {
            return "Null object table";
        }
    };

    /**
     * Return the null object instance table.  This instance contains no predefined instances.
     *
     * @return the null instance table
     */
    public static ObjectTable nullObjectTable() {
        return NULL_OBJECT_TABLE;
    }

    private static final ClassTable NULL_CLASS_TABLE = new ClassTable() {
        public Writer getClassWriter(final Class<?> clazz) {
            return null;
        }

        public Class<?> readClass(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
            return null;
        }

        public String toString() {
            return "Null class table";
        }
    };

    /**
     * Return the null class table instance.  This instance contains no predefined classes.
     *
     * @return the null class table
     */
    public static ClassTable nullClassTable() {
        return NULL_CLASS_TABLE;
    }

    /**
     * Construct a new {@link java.io.MyOptionalDataException}.  This method is necssary because there are no
     * public constructors in the API.
     *
     * @param eof {@code true} if there is no more data in the buffered part of the stream
     * @return a new MyOptionalDataException
     */
    public static CustomOptionalDataException createOptionalDataException(boolean eof) {
        final CustomOptionalDataException MyOptionalDataException = createMyOptionalDataException();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        final StackTraceElement[] copyStackTrace = new StackTraceElement[stackTrace.length - 1];
        System.arraycopy(stackTrace, 1, copyStackTrace, 0, copyStackTrace.length);
        MyOptionalDataException.setStackTrace(copyStackTrace);
        MyOptionalDataException.eof = eof;
        return MyOptionalDataException;
    }

    /**
     * Construct a new {@link java.io.MyOptionalDataException}.  This method is necssary because there are no
     * public constructors in the API.
     *
     * @param length the number of bytes of primitive data available to be read in the current buffer
     * @return a new MyOptionalDataException
     */
    public static CustomOptionalDataException createOptionalDataException(int length) {
        final CustomOptionalDataException MyOptionalDataException = createMyOptionalDataException();
        MyOptionalDataException.length = length;
        return MyOptionalDataException;
    }

    private static CustomOptionalDataException createMyOptionalDataException() {
        return AccessController.doPrivileged(OPTIONAL_DATA_EXCEPTION_CREATE_ACTION);
    }

    private static final MyOptionalDataExceptionCreateAction OPTIONAL_DATA_EXCEPTION_CREATE_ACTION = new MyOptionalDataExceptionCreateAction();

    private static final class MyOptionalDataExceptionCreateAction implements PrivilegedAction<CustomOptionalDataException> {

        private final Constructor<CustomOptionalDataException> constructor;

        private MyOptionalDataExceptionCreateAction() {
            constructor = AccessController.doPrivileged(new PrivilegedAction<Constructor<CustomOptionalDataException>>() {
                public Constructor<CustomOptionalDataException> run() {
                    try {
                        final Constructor<CustomOptionalDataException> constructor = CustomOptionalDataException.class.getDeclaredConstructor(boolean.class);
                        constructor.setAccessible(true);
                        return constructor;
                    } catch (NoSuchMethodException e) {
                        throw new NoSuchMethodError(e.getMessage());
                    }
                }
            });
        }

        public CustomOptionalDataException run() {
            try {
                return constructor.newInstance(Boolean.FALSE);
            } catch (InstantiationException e) {
                throw new InstantiationError(e.getMessage());
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Error invoking constructor", e);
            }
        }
    }

    private static final Externalizer NULL_EXTERNALIZER = new AbstractExternalizer() {
        private static final long serialVersionUID = 1L;

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
        }

        public void readExternal(final Object subject, final ObjectInput input) throws IOException, ClassNotFoundException {
        }

        public String toString() {
            return "Null externalizer";
        }
    };

    /**
     * Get a null externalizer.  Useful in conjunction with {@link org.jboss.marshalling.ObjectTable} entries.
     * This externalizer reads and writes no data.
     *
     * @return the null externalizer
     */
    public static Externalizer nullExternalizer() {
        return NULL_EXTERNALIZER;
    }
}
