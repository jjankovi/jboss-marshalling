package org.jboss.marshalling;

import java.io.ObjectStreamException;

public class CustomOptionalDataException extends ObjectStreamException {

    private static final long serialVersionUID = -8011121865681257820L;

    /*
     * Create an <code>OptionalDataException</code> with a length.
     */
    public CustomOptionalDataException(int len) {
        eof = false;
        length = len;
    }

    /*
     * Create an <code>OptionalDataException</code> signifying no
     * more primitive data is available.
     */
    public CustomOptionalDataException(boolean end) {
        length = 0;
        eof = end;
    }

    /**
     * The number of bytes of primitive data available to be read
     * in the current buffer.
     *
     * @serial
     */
    public int length;

    /**
     * True if there is no more data in the buffered part of the stream.
     *
     * @serial
     */
    public boolean eof;
}
