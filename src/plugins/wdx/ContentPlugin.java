package plugins.wdx;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;

import java.util.*;

import plugins.wdx.WDXPluginAdapter;
import plugins.wdx.FieldValue;
import static plugins.wdx.FieldValue.*;

public abstract class ContentPlugin extends WDXPluginAdapter {

    public static final int FT_SETSUCCESS = 0; // missing in tc-apis
    
    public static abstract class Field<T> {
    
        /* Found that (my) TotalCommander on my (my) WinXP always passes 259 as
         * the max name length including the trailing 0 to
         * contentGetSupportedField(..) so this is the bound to be checked in
         * runTests().
         * <p>
         * Note that another check against the actual value passed to
         * contentGetSupportedField is performed on invocation of that method.
         */
        public static final int MAX_NAME_LENGTH = 258;

        public static abstract class STRING extends Field<String> {
            protected STRING(String name) {
                super(name, FT_STRING, String.class);
            }
            final String _getValue(String fileName) throws IOException {
                return getValue(fileName);
            }
            public abstract String getValue(String fileName) throws IOException;
        }

        public static abstract class INT extends Field<Integer> {
            protected INT(String name) {
                super(name, FT_NUMERIC_32, Integer.class);
            }
            final Integer _getValue(String fileName) throws IOException {
                return getValue(fileName);
            }
            public abstract int getValue(String fileName) throws IOException;
        }

        public final String name;
        public final int type;
        public final Class<T> javaType;

        private Field(String name, int type, Class<T> javaType) {
            this.name = name;
            this.type = type;
            this.javaType = javaType;
            assertValidName();
        }

        abstract T _getValue(String fileName) throws IOException;

        public final boolean isEditable() {
            return this instanceof EditableField;
        }
        
        public boolean isDelayInOrder(String fileName) throws IOException {
            return false;
        }

        public String toString() {
            return javaType.getName().replace("java.lang.", "") + " " + name;
        }
        
        public final void assertValidNameLength() {
            assertValidNameLength(MAX_NAME_LENGTH);
        }

        public final void assertValidNameLength(int maxlen) {
            if (this.name.length() > maxlen)
                throw new IllegalArgumentException("field name too long (" + name.length() + " > " + maxlen + "): \"" + name + "\"");
        }
        
        public final void assertValidName() {
            if (name == null) {
                throw new IllegalArgumentException("field name must not be null!");
            }
            assertValidNameLength();
            if (name.contains(".") || name.contains("|") || name.contains(":")) {
                throw new IllegalArgumentException("field name must not contain '.', '|' or ':': \"" + name + "\"");
            }
        }
    }

    public static abstract class EditableField<T> extends Field<T> {

        public static abstract class STRING extends EditableField<String> {
            protected STRING(String name) {
                super(name, FT_STRING, String.class);
            }
            final String _getValue(String fileName) throws IOException {
                return getValue(fileName);
            }
            final void _setValue(String fileName, FieldValue fieldValue) throws IOException {
                setValue(fileName, fieldValue.getStr());
            }
            public abstract String getValue(String fileName) throws IOException;
            public abstract void setValue(String fileName, String value) throws IOException;
        }

        public static abstract class INT extends EditableField<Integer> {
            protected INT(String name) {
                super(name, FT_NUMERIC_32, Integer.class);
            }
            final Integer _getValue(String fileName) throws IOException {
                return getValue(fileName);
            }
            final void _setValue(String fileName, FieldValue fieldValue) throws IOException {
                setValue(fileName, fieldValue.getIntValue());
            }
            public abstract int getValue(String fileName) throws IOException;
            public final void setValue(String fileName, Integer value) throws IOException {
                setValue(fileName, (int)value);
            }
            public abstract void setValue(String fileName, int value) throws IOException;
        }

        private EditableField(String name, int type, Class<T> javaType) {
            super(name, type, javaType);
        }

        abstract void _setValue(String fileName, FieldValue value) throws IOException;

        public abstract void setValue(String fileName, T value) throws IOException;

    }

    private final Log myLog;
    protected final Log log;

    protected ContentPlugin() {
        this.myLog = LogFactory.getLog(ContentPlugin.class);
        this.log = LogFactory.getLog(this.getClass());
        initFields();
    }
    
    protected abstract void initFields();

    private Map<String, Field<?>> namesToFields = new HashMap<>();
    private List<Field<?>> fields = new ArrayList<>();

    private boolean hasEditableFields = false;

    protected final <T, F extends Field<T>> void define(F field) {
        if (namesToFields.containsKey(field.name)) {
            throw new RuntimeException("duplicate field name \"" + field.name + "\"");
        }
        namesToFields.put(field.name, field);
        fields.add(field);
        hasEditableFields |= field.isEditable();
    }
    
    public Iterable<Field<?>> fields() {
        return this.fields;
    }
    
    public <T> Field<T> getField(String name) {
        return (Field<T>)namesToFields.get(name);
    }
    
    public <T> T getValue(String fieldName, String fileName) throws IOException {
        Field<T> field = this.<T>getField(fieldName);
        return field._getValue(fileName);
    }

    public void listFields() {
        listFields(System.out);
    }

    public void listFields(PrintStream out) {
        int n = 0;
        for (Field<?> f: fields()) {
            out.println(n++ + "\t" + f);
        }
    }

    public final int contentGetSupportedField(int fieldIndex,
                                                StringBuffer fieldName,
                                                StringBuffer units,
                                                int maxlen)
        {

        myLog.trace("contentGetSupportedField(" + fieldIndex + ",...," + maxlen + ")");

        if (fieldIndex >= fields.size()) {
            return FT_NOMOREFIELDS;
        }
        Field<?> field = fields.get(fieldIndex);
        field.assertValidNameLength(maxlen - 1);    // maxlen includes the trailing 0
        fieldName.append(field.name);
        return field.type;
    }

    @Override
    public final int contentGetSupportedFieldFlags(final int fieldIndex) {
        myLog.info("contentGetSupportedFieldFlags(" + fieldIndex + ")");
        if (fieldIndex == -1) {
            return hasEditableFields ? CONTFLAGS_EDIT : 0;// | CONTFLAGS_FIELDEDIT;
        }
        if (fieldIndex >= fields.size()) {
            return FT_NOSUCHFIELD;
        }
        Field<?> field = fields.get(fieldIndex);
        if (field.isEditable()) {
            myLog.info("contentGetSupportedFieldFlags(" + fieldIndex + "): " + field.name + " is editable!");
            return CONTFLAGS_EDIT;
        }
        return 0;
    }

    public final int contentGetValue(String fileName,
                                        int fieldIndex,
                                        int unitIndex,
                                        FieldValue fieldValue,
                                        int maxlen,
                                        int flags)
        {
        myLog.trace("contentGetValue('" + fileName + "', " + fieldIndex + ",...," + maxlen + ", " + flags + ")");
        if (fieldIndex >= fields.size()) {
            return FT_NOSUCHFIELD;
        }
        Field<?> field = fields.get(fieldIndex);
        try {
            if ((flags & CONTENT_DELAYIFSLOW) != 0) {
                if (field.isDelayInOrder(fileName)) {
                    myLog.info("delayed " + field.name + ".getValue(\"" + fileName + "\").");
                    return FT_DELAYED;
                }
            }
            Object value = field._getValue(fileName);
            myLog.info(field.name + "=" + value);
            fieldValue.setValue(field.type, value);
            return field.type;
        } catch (IOException e) {
            log.error(e);
            return FT_FILEERROR;
        }
    }

    public final int contentSetValue(final String fileName,
                                       final int fieldIndex,
                                       final int unitIndex,
                                       final int fieldType,
                                       final FieldValue fieldValue,
                                       final int flags)
    {
        if (fieldIndex == -1) { // end of change attributes
            return FT_SETSUCCESS;
        } else if ((fieldIndex < 0) || (fieldIndex >= fields.size())) {
            return FieldValue.FT_NOSUCHFIELD;
        }
        Field<?> field = fields.get(fieldIndex);
        if (!field.isEditable()) {
            return FT_FILEERROR;
        }
        try {
            ((EditableField)field)._setValue(fileName, fieldValue);
        } catch (IOException e) {
            log.error(e);
            return FT_FILEERROR;
        }
        return FT_SETSUCCESS;
    }


    public void runTests(String... fileNames) throws IOException {
        
        // defines at least one field
        if (this.fields.size() < 0) {
            throw new RuntimeException(this.getClass().getName() + " should define at least 1 field");
        }
        
        // no field name too long
        for (Field<?> f: fields) {
            f.assertValidNameLength();
        }
        
        for (String fileName: fileNames) {
            for (Field<?> f: fields) {
                Object result = f._getValue(fileName);
                System.out.println("TEST " + f + " on \"" + fileName + "\": " + result);
            }
        }
    }

}
