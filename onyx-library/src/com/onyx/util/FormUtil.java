package com.onyx.util;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.layout.Pane;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by cosbor11 on 3/3/2015.
 */
public class FormUtil {

    public static String camelCaseToLabel(String s) {
        return StringUtils.capitalize(s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        ));
    }

    public static void cascade(Pane root, Consumer<Control> consumer) {
        for (Node node : root.getChildren()) {
            if (node instanceof Pane) {
                cascade((Pane) node, consumer);
            } else if (node instanceof Control) {
                consumer.accept((Control) node);
            }
        }
    }

    public static List<Control> getControls(Pane rootPane)
    {
        List<Control> controls = new ArrayList<Control>();

        for (Node node : rootPane.getChildren()) {
            if (node instanceof Pane) {
                getControls((Pane) node, controls);
            } else if (node instanceof Control) {
                controls.add((Control) node);
            }
        }

        return controls;

    }

    public static List<Control> getControls(Pane rootPane, Class clazz)
    {
        List<Control> controls = new ArrayList<Control>();

        for (Node node : rootPane.getChildren()) {
            if (node instanceof Pane) {
                getControls((Pane) node, clazz, controls);
            } else if (clazz.isAssignableFrom(node.getClass())) {
                controls.add((Control) node);
            }
        }

        return controls;

    }

    public static List<Control> getControls(Pane rootPane, Class clazz, List<Control> controls)
    {

        for (Node node : rootPane.getChildren()) {
            if (node instanceof Pane) {
                getControls((Pane) node, clazz, controls);
            } else if (clazz.isAssignableFrom(node.getClass())) {
                controls.add((Control) node);
            }
        }

        return controls;

    }

    public static List<Control> getControls(Pane rootPane, List<Control> controls)
    {

        for (Node node : rootPane.getChildren()) {
            if (node instanceof Pane) {
                getControls((Pane) node, controls);
            } else if (node instanceof Control) {
                controls.add((Control) node);
            }
        }

        return controls;

    }
}
