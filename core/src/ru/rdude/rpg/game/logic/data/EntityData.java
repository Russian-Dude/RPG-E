package ru.rdude.rpg.game.logic.data;

import ru.rdude.rpg.game.logic.data.resources.Resources;
import ru.rdude.rpg.game.logic.enums.EntityReferenceInfo;

import java.util.HashSet;
import java.util.Set;


public abstract class EntityData {
    private long guid;
    private Set<Long> moduleDependencies;
    private String name;
    private String nameInEditor;
    private String description;
    private EntityReferenceInfo entityReferenceInfo;
    private EntityReferenceInfo entityInfo;

    private Resources resources;

    // if true this entity will not be used as entity. Instead it accumulate entities that correspond to this entity fields
    private boolean describer;

    EntityData() {
    }

    public EntityData(long guid) {
        this.guid = guid;
        this.name = "";
        this.description = "";
        moduleDependencies = new HashSet<>();
    }

    public long getGuid() {
        return guid;
    }

    public void setGuid(long guid) {
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNameInEditor() {
        return nameInEditor;
    }

    public void setNameInEditor(String nameInEditor) {
        this.nameInEditor = nameInEditor;
    }

    public boolean isDescriber() {
        return describer;
    }

    public void setDescriber(boolean describer) {
        this.describer = describer;
    }

    public EntityReferenceInfo getEntityReferenceInfo() {
        return entityReferenceInfo;
    }

    public void setEntityReferenceInfo(EntityReferenceInfo entityReferenceInfo) {
        this.entityReferenceInfo = entityReferenceInfo;
    }

    public EntityReferenceInfo getEntityInfo() {
        return entityInfo;
    }

    public void setEntityInfo(EntityReferenceInfo entityInfo) {
        this.entityInfo = entityInfo;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public Set<Long> getModuleDependencies() {
        return moduleDependencies;
    }

    public void setModuleDependencies(Set<Long> moduleDependencies) {
        this.moduleDependencies = moduleDependencies;
    }

    public void addModuleDependency(Long guid) {
        this.moduleDependencies.add(guid);
    }

    public void addModuleDependency(Module entityData) {
        addModuleDependency(entityData.getGuid());
    }

    public void removeModuleDependency(Long guid) {
        this.moduleDependencies.remove(guid);
    }

    public void removeModuleDependency(EntityData entityData) {
        removeModuleDependency(entityData.getGuid());
    }

    public abstract boolean hasEntityDependency(long guid);

    public abstract void replaceEntityDependency(long oldValue, long newValue);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityData)) return false;

        EntityData that = (EntityData) o;

        return guid == that.guid;
    }

    @Override
    public int hashCode() {
        return (int) (guid ^ (guid >>> 32));
    }
}
