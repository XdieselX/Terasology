/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.nui.editor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.math.Border;
import org.terasology.rendering.nui.LayoutConfig;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.contextMenu.ContextMenuBuilder;
import org.terasology.rendering.nui.contextMenu.ContextMenuLevel;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.skin.UISkin;
import org.terasology.rendering.nui.widgets.UpdateListener;
import org.terasology.rendering.nui.widgets.treeView.JsonTree;
import org.terasology.rendering.nui.widgets.treeView.JsonTreeValue;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NUIEditorContextMenuBuilder {

    private Logger logger = LoggerFactory.getLogger(NUIEditorContextMenuBuilder.class);

    // Context menu options.
    public static final String OPTION_ADD_EXTENDED = "Add...";
    public static final String OPTION_ADD_WIDGET = "Add Widget";
    public static final String OPTION_COPY = "Copy";
    public static final String OPTION_EDIT = "Edit";
    public static final String OPTION_PASTE = "Paste";

    /**
     * A {@link NUIManager} instance retrieved from the editor screen.
     */
    private NUIManager nuiManager;
    /**
     * External consumer methods to be used when creating the menu.
     */
    private Map<String, Consumer<JsonTree>> externalConsumers = Maps.newHashMap();
    /**
     * Listeners fired when one of the options within {@code LEVEL_ADD_EXTENDED} is selected.
     */
    private List<UpdateListener> addContextMenuListeners = Lists.newArrayList();

    public void setManager(NUIManager nuiManager) {
        this.nuiManager = nuiManager;
    }

    public void putConsumer(String key, Consumer<JsonTree> value) {
        externalConsumers.put(key, value);
    }

    public void subscribeAddContextMenu(UpdateListener listener) {
        addContextMenuListeners.add(listener);
    }

    public ContextMenuBuilder createPrimaryContextMenu(JsonTree node) {
        ContextMenuBuilder contextMenu = new ContextMenuBuilder();
        ContextMenuLevel primaryLevel = contextMenu.addLevel(true);

        JsonTreeValue.Type type = node.getValue().getType();

        // Create the ADD_EXTENDED level.
        if ((type == JsonTreeValue.Type.ARRAY && !node.getValue().getKey().equals("contents"))
            || type == JsonTreeValue.Type.OBJECT) {
            ContextMenuLevel addLevel = contextMenu.addLevel(false);
            primaryLevel.addNavigationOption(OPTION_ADD_EXTENDED, addLevel);
            createAddContextMenu(node, addLevel);
        }

        // If the node is a "contents" array, add the widget addition option (redirects to WidgetSelectionScreen).
        if (type == JsonTreeValue.Type.ARRAY && node.getValue().getKey().equals("contents")) {
            primaryLevel.addOption(OPTION_ADD_WIDGET, externalConsumers.get(OPTION_ADD_WIDGET), node, true);
        }

        // Always add the copy&paste options.
        primaryLevel.addOption(OPTION_COPY, externalConsumers.get(OPTION_COPY), node, true);
        primaryLevel.addOption(OPTION_PASTE, externalConsumers.get(OPTION_PASTE), node, true);

        // Unless the node is an OBJECT child of an ARRAY (should always have an empty key), add the edit option.
        if (type != JsonTreeValue.Type.NULL && !(type == JsonTreeValue.Type.OBJECT
                                                 && !node.isRoot()
                                                 && node.getParent().getValue().getType() == JsonTreeValue.Type.ARRAY)) {
            primaryLevel.addOption(OPTION_EDIT, externalConsumers.get(OPTION_EDIT), node, true);
        }

        return contextMenu;
    }

    public ContextMenuBuilder createPrimarySkinContextMenu(JsonTree node) {
        ContextMenuBuilder contextMenu = new ContextMenuBuilder();
        ContextMenuLevel primaryLevel = contextMenu.addLevel(true);

        JsonTreeValue.Type type = node.getValue().getType();

        // Create the ADD_EXTENDED level.
        if (type == JsonTreeValue.Type.ARRAY || (type == JsonTreeValue.Type.OBJECT
                                                 && !(node.getValue().getKey() != null &&
                                                      node.getValue().getKey().equals("elements")))) {
            ContextMenuLevel addLevel = contextMenu.addLevel(false);
            primaryLevel.addNavigationOption(OPTION_ADD_EXTENDED, addLevel);
            createAddSkinContextMenu(node, addLevel);
        }

        // If the node is an "elements" object, add the widget addition option (redirects to WidgetSelectionScreen).
        if (type == JsonTreeValue.Type.OBJECT && node.getValue().getKey() != null &&
            node.getValue().getKey().equals("elements")) {
            primaryLevel.addOption(OPTION_ADD_WIDGET, externalConsumers.get(OPTION_ADD_WIDGET), node, true);
        }

        // Always add the copy&paste and edit options.
        primaryLevel.addOption(OPTION_COPY, externalConsumers.get(OPTION_COPY), node, true);
        primaryLevel.addOption(OPTION_PASTE, externalConsumers.get(OPTION_PASTE), node, true);
        primaryLevel.addOption(OPTION_EDIT, externalConsumers.get(OPTION_EDIT), node, true);

        return contextMenu;
    }

    public void createAddContextMenu(JsonTree node, ContextMenuLevel addLevel) {
        JsonTreeValue.Type type = node.getValue().getType();

        if (type == JsonTreeValue.Type.ARRAY) {
            // Add generic item addition options.
            addLevel.addOption("Boolean value", n -> {
                n.addChild(new JsonTreeValue(null, false, JsonTreeValue.Type.VALUE));
                addContextMenuListeners.forEach(UpdateListener::onAction);
            }, node, true);
            addLevel.addOption("Number value", n -> {
                n.addChild(new JsonTreeValue(null, 0.0f, JsonTreeValue.Type.VALUE));
                addContextMenuListeners.forEach(UpdateListener::onAction);
            }, node, true);
            addLevel.addOption("String value", n -> {
                n.addChild(new JsonTreeValue(null, "", JsonTreeValue.Type.VALUE));
                addContextMenuListeners.forEach(UpdateListener::onAction);
            }, node, true);
        } else if (type == JsonTreeValue.Type.OBJECT) {
            // Add a generic key/value pair addition option.
            addLevel.addOption("Key/value pair", n -> {
                n.addChild(new JsonTreeValue("", "", JsonTreeValue.Type.KEY_VALUE_PAIR));
                addContextMenuListeners.forEach(UpdateListener::onAction);
            }, node, true);

            populateContextMenu(node, addLevel, false);
        }
    }

    public void createAddSkinContextMenu(JsonTree node, ContextMenuLevel addLevel) {
        JsonTreeValue.Type type = node.getValue().getType();

        if (type == JsonTreeValue.Type.OBJECT) {
            if (node.getValue().getKey() != null && node.getValue().getKey().equals("families")) {
                addLevel.addOption("New family", n -> {
                    n.addChild(new JsonTreeValue("", null, JsonTreeValue.Type.OBJECT));
                    n.getChildAt(0).setExpanded(true);
                    addContextMenuListeners.forEach(UpdateListener::onAction);
                }, node, true);
            } else {
                addLevel.addOption("Key/value pair", n -> {
                    n.addChild(new JsonTreeValue("", "", JsonTreeValue.Type.KEY_VALUE_PAIR));
                    addContextMenuListeners.forEach(UpdateListener::onAction);
                }, node, true);

                populateContextMenu(node, addLevel, true);
            }
        }
    }

    private void populateContextMenu(JsonTree node, ContextMenuLevel addLevel, boolean isSkin) {
        Class clazz = null;
        if (isSkin) {
            clazz = NUIEditorNodeUtils.getSkinNodeClass(node, nuiManager);
        } else {
            clazz = NUIEditorNodeUtils.getNodeClass(node, nuiManager);
        }

        if (clazz != null) {
            for (Field field : ReflectionUtils.getAllFields(clazz)) {
                if (!UIWidget.class.isAssignableFrom(clazz) || field.isAnnotationPresent(LayoutConfig.class)) {
                    field.setAccessible(true);
                    String name = getNodeName(field);
                    Class finalClazz = clazz;
                    if (!node.hasChildWithKey(name)) {
                        addLevel.addOption(name, n -> {
                            try {
                                createChild(name, node, field, finalClazz);
                                addContextMenuListeners.forEach(UpdateListener::onAction);
                            } catch (IllegalAccessException | InstantiationException e) {
                                logger.warn("Could not add child", e);
                            }
                        }, node, true);
                    }
                }
            }
        } else {
            logger.warn("Could not get class for node ", node.getValue().toString());
        }
    }

    private void createChild(String name, JsonTree node, Field field, Class clazz)
        throws IllegalAccessException, InstantiationException {
        JsonTreeValue childValue = new JsonTreeValue();
        childValue.setKey(name);

        if (UISkin.class.isAssignableFrom(field.getType())) {
            // Skin fields should always be KEY_VALUE_PAIR nodes with string values.
            childValue.setValue("engine:default");
            childValue.setType(JsonTreeValue.Type.KEY_VALUE_PAIR);
        } else {
            if (isWidget(field)) {
                createWidgetChild(name, node);
                return;
            } else {
                childValue.setValue(getFieldValue(field, clazz));
                childValue.setType(getNodeType(field, getFieldValue(field, clazz)));
            }
        }

        JsonTree child = new JsonTree(childValue);
        child.setExpanded(true);
        node.addChild(child);
    }

    private boolean isWidget(Field field) throws IllegalAccessException {
        // The field is a Binding<? extends UIWidget>.
        if (Binding.class.isAssignableFrom(field.getType())
            && UIWidget.class.isAssignableFrom((Class<?>)
            ((ParameterizedType) (field.getGenericType())).getActualTypeArguments()[0])) {
            return true;
        }

        // The field is UIWidget or its' override.
        if (UIWidget.class.isAssignableFrom(field.getType())) {
            return true;
        }
        return false;
    }

    private void createWidgetChild(String name, JsonTree node) {
        JsonTree widgetTree = new JsonTree(new JsonTreeValue(name, null, JsonTreeValue.Type.OBJECT));
        NUIEditorNodeUtils
            .createNewWidget("UILabel", "newWidget", false)
            .getChildren()
            .forEach(widgetTree::addChild);
        widgetTree.addChild(new JsonTreeValue("text", "", JsonTreeValue.Type.KEY_VALUE_PAIR));
        node.addChild(widgetTree);
    }

    private String getNodeName(Field field) {
        return field.isAnnotationPresent(SerializedName.class)
            ? field.getAnnotation(SerializedName.class).value() : field.getName();
    }

    private JsonTreeValue.Type getNodeType(Field field, Object value) {
        return Enum.class.isAssignableFrom(field.getType()) || value instanceof UISkin
               || value instanceof Boolean || value instanceof String || value instanceof Number
            ? JsonTreeValue.Type.KEY_VALUE_PAIR : JsonTreeValue.Type.OBJECT;
    }

    private Object getFieldValue(Field field, Class clazz) throws IllegalAccessException, InstantiationException {
        if (Enum.class.isAssignableFrom(field.getType())) {
            return field.getType().getEnumConstants()[0].toString();
        } else {
            Object value;
            if (Binding.class.isAssignableFrom(field.getType())) {
                Binding binding = (Binding) field.get(newInstance(clazz));
                value = binding.get();
            } else {
                value = field.get(newInstance(clazz));
            }

            if (value != null && value instanceof Boolean) {
                value = !(Boolean) value;
            }

            return value != null ? value : newInstance(field.getType());
        }
    }

    private Object newInstance(Class clazz) throws IllegalAccessException, InstantiationException {
        if (Boolean.class.isAssignableFrom(clazz)) {
            return false;
        }
        if (Border.class.isAssignableFrom(clazz)) {
            return Border.ZERO;
        }
        if (Number.class.isAssignableFrom(clazz)) {
            return 0;
        }
        return clazz.newInstance();
    }
}