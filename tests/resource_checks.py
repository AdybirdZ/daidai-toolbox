"""Resource migration checks: language lookup must never alter durable data identities."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
java=root/'app/src/main/java/org/simpleweather/app'
values=root/'app/src/main/res/values'
strings={};arrays={}
for path in values.glob('*.xml'):
 for e in ET.parse(path).getroot():
  k=e.attrib.get('name')
  if e.tag=='string':
   assert k not in strings,('Duplicate text key',k)
   strings[k]=e
  elif e.tag=='string-array':arrays[k]=[i.text for i in e]
for path in java.glob('*.java'):
 text=path.read_text(encoding='utf8')
 for method,key in re.findall(r'UiText\.(get|raw)\("([a-z_0-9]+)"\)',text):
  assert key in strings,(path.name,'Missing XML string',key)
  if method=='raw':assert strings[key].attrib.get('translatable')=='false',(path.name,'Identity must not be localized',key)
 if path.name!='City.java':
  tokens=re.findall(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:[^"\\]|\\.)*"',text)
  assert not any(t.startswith('"') and re.search(r'[\u4e00-\u9fff]',t) for t in tokens),(path.name,'Hardcoded Chinese UI text')
assert len(arrays['identity_keys'])==len(arrays['identity_label_keys'])
for raw,label in zip(arrays['identity_keys'],arrays['identity_label_keys']):
 assert raw in strings and label in strings
 assert strings[raw].attrib.get('translatable')=='false'
assert 'relative?"' not in (java/'StatusViews.java').read_text(encoding='utf8')
print('XML resource references, stable identifiers and historical UI migration verified')
