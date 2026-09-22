SKIPUNZIP=0

ui_print "***************************************************"
ui_print "*             PUFFIN OS 10 CONVERTER              *"
ui_print "*      Cloud-First Avatar OS for LineageOS 22.2   *"
ui_print "***************************************************"

ui_print "- Target System: Android 15 (LineageOS 22.2)"
ui_print "- Installing Puffin Avatar System Hub Shell..."
ui_print "- Configuring Puffin Cloud Rendering Pipeline..."

# Set executable permissions
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/system.prop 0 0 0644

# System Priv-App permissions
if [ -d "$MODPATH/system/priv-app" ]; then
    set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644
fi

ui_print "- Puffin OS 10 Transformation Complete!"
ui_print "- Please reboot your device to launch into Puffin OS 10."
