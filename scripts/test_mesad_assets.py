from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ICONS=ROOT/'mesad-assets/icons'
ANDROID=ROOT/'mesad-android/app/src/main/res'

def test_icon_export_dimensions_and_coverage():
    expected={'mesad_icon_main_1024.png':(1024,1024),'mesad_icon_main_512.png':(512,512),'mesad_icon_minimal_512.png':(512,512),'mesad_icon_monochrome_512.png':(512,512),'adaptive_foreground_432.png':(432,432),'adaptive_background_432.png':(432,432),'adaptive_monochrome_432.png':(432,432)}
    for file,size in expected.items():
        with Image.open(ICONS/file) as img:
            assert img.size==size

def test_adaptive_foreground_essential_art_fits_66dp_safe_zone():
    with Image.open(ICONS/'adaptive_foreground_432.png').convert('RGBA') as icon:
        alpha=icon.getchannel('A')
        x0,y0,x1,y1=alpha.getbbox()
        safe_min=432*(108-66)/(2*108)
        safe_max=432-safe_min
        assert x0>=safe_min-2 and y0>=safe_min-2
        assert x1<=safe_max+2 and y1<=safe_max+2

def test_android_adaptive_icon_has_all_three_layers():
    ns={'android':'http://schemas.android.com/apk/res/android'}
    tree=ET.parse(ANDROID/'mipmap-anydpi-v26/ic_launcher.xml')
    root=tree.getroot()
    assert root.tag=='adaptive-icon'
    assert root.find('background',ns) is not None
    assert root.find('foreground',ns) is not None
    assert root.find('monochrome',ns) is not None
    for name in ('ic_launcher_background.xml','ic_launcher_foreground.xml','ic_launcher_monochrome.xml'):
        vector=ET.parse(ANDROID/'drawable'/name).getroot()
        assert vector.get(f"{{{ns['android']}}}viewportWidth")=="108"
        assert vector.get(f"{{{ns['android']}}}viewportHeight")=="108"
