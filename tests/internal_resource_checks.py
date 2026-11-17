"""Check translations and licence assets for downloadable internal applications."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re,collections
root=Path(__file__).resolve().parents[1]
def values(folder):
 result={}
 for file in folder.glob('*.xml'):
  for item in ET.parse(file).getroot():
   if item.tag!='string':continue
   name=item.get('name');assert name not in result,(file,name)
   result[name]=''.join(item.itertext())
 return result
for app in ('shared','music','messaging','colorvision'):
 folder=root/'modules/embedded'/app/'res';base=values(folder/'values');en=values(folder/'values-en')
 assert base.keys()==en.keys(),(app,base.keys()-en.keys(),en.keys()-base.keys())
 for name,text in base.items():
  pattern=r'%(?:\d+\$)?[-+0-9.]*[a-zA-Z]'
  assert collections.Counter(re.findall(pattern,text))==collections.Counter(re.findall(pattern,en[name])),(app,name)
 zh=values(folder/'values-zh')
 if zh:assert zh==base,(app,'Chinese resources differ')
 print(f'Internal resources: {app}: {len(base)} translated strings')
assert (root/'modules/embedded/colorvision/assets/licenses/DaltonLens-MIT.txt').is_file()
