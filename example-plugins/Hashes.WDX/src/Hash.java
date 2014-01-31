

import java.nio.ByteBuffer;

import java.util.zip.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public enum Hash {
    CRC32(32, Factory.fromChecksum(java.util.zip.CRC32.class)),
    Adler32(32, Factory.fromChecksum(java.util.zip.Adler32.class)),
    MD2(128, Factory.fromMessageDigest("MD2")),
    MD5(128, Factory.fromMessageDigest("MD5")),
    SHA1(160, Factory.fromMessageDigest("SHA1")),
    SHA256(256, Factory.fromMessageDigest("SHA-256")),
    SHA384(384, Factory.fromMessageDigest("SHA-384")),
    SHA512(512, Factory.fromMessageDigest("SHA-512"));


    public static abstract class Processor {

        public final Hash hash;

        Processor(Hash hash) {
            this.hash = hash;
        }

        public abstract Processor update(ByteBuffer buf);

        public abstract byte[] getValue();

        public String getValueAsHex() {
            StringBuilder result = new StringBuilder();
            byte[] bytes = this.getValue();
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                if (b < 0x10) {
                    result.append(0);
                }
                result.append(Integer.toString(b, 16));
            }
            return result.toString();
        }

    }

    static abstract class Factory {

        public abstract Processor newProcessor(Hash hash);

        public static Factory fromChecksum(final Class<? extends java.util.zip.Checksum> checksumClass) {
            return new Factory() {

                Checksum checksumInstance() {
                    try {
                        return checksumClass.newInstance();
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InstantiationException e) {
                        throw new RuntimeException(e);
                    }
                }

                public Processor newProcessor(final Hash hash) {
                    return new Processor(hash) {
                        private Checksum cs = checksumInstance();
                        private byte[] byteArray = null;

                        public Processor update(ByteBuffer buf) {
                            if (buf.hasArray()) {
                                byte[] byteArray = buf.array();
                                cs.update(byteArray, buf.arrayOffset(), buf.remaining());
                                buf.position(buf.limit());
                            } else {
                                if (this.byteArray == null) {
                                    this.byteArray = new byte[buf.capacity()];
                                }
                                while (buf.remaining() >= this.byteArray.length) {
                                    buf.get(this.byteArray);
                                }
                                int n = buf.remaining();
                                if (n > 0) {
                                    buf.get(this.byteArray, 0, n);
                                    cs.update(this.byteArray, 0, n);
                                }
                            }
                            return this;
                        }

                        public byte[] getValue() {
                            final long value = cs.getValue();
                            return new byte[] { (byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value };
                        }
                    };
                }
            };
        }

        public static Factory fromMessageDigest(final String algorithmName) {
            return new Factory() {

                public Processor newProcessor(final Hash hash) {
                    try {
                        return new Processor(hash) {
                            MessageDigest md = MessageDigest.getInstance(algorithmName);

                            public Processor update(ByteBuffer buf) {
                                md.update(buf);
                                return this;
                            }

                            public byte[] getValue() {
                                return md.digest();
                            }
                        };
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    }

/* ------------------------------------------------------------------------------------------ */

    private final int bitLength;
    private Factory factory;

    Hash(int bitLength, Factory factory) {
        this.bitLength = bitLength;
        this.factory = factory;
    }

    public int bitLength() {
        return this.bitLength;
    }

    public Processor newProcessor() {
        return this.factory.newProcessor(this);
    }

}