package ua.kpi.trpz.nantbuilder.model;

/**
 * Властивість NAnt ({@code <property name="..." value="..."/>}), доступна в скрипті як {@code ${name}}.
 */
public class ScriptProperty {
    private Long id;
    private Long scriptId;
    private String name;
    private String value;
    private boolean readOnly;

    /** Створює властивість із заданою назвою та значенням. */
    public ScriptProperty(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public void setScriptId(Long scriptId) {
        this.scriptId = scriptId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
