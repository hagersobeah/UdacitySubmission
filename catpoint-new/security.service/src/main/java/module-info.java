module security.service {
    requires com.google.common;
    requires com.miglayout.swing;
    requires image.service;
    requires java.desktop;
    requires com.google.gson;
    requires java.prefs;
    opens com.udacity.catpoint.security.data to com.google.gson;
}