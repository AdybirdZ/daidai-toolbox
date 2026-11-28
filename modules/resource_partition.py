"""Partition legacy app resources using the frozen API resource IDs and actual source references."""
from pathlib import Path
import re, shutil, xml.etree.ElementTree as ET, json

IDS=['weather','schedule','status','weather-map','astronomy','earthquake','random','todo']

def partition(root, build_dir=None):
    root=Path(root);source=root/'app/src/main/res';stage=(Path(build_dir) if build_dir is not None else root/'build')/'partitioned-res'
    if stage.exists():shutil.rmtree(stage)
    owners={'host':list((root/'app/src/main/java').rglob('*.java'))}
    owners.update({id:list((root/'modules/apps'/id/'java').rglob('*.java')) for id in IDS})
    values={};files={}
    for p in source.rglob('*'):
        if not p.is_file():continue
        if p.parent.name.startswith('values'):
            for node in ET.parse(p).getroot():
                name=node.get('name');kind=node.tag
                if kind in ['string-array','integer-array']:kind='array'
                if name:values.setdefault(kind+'/'+name,[]).append((p.parent.name,node))
        else:files[p.parent.name.split('-')[0]+'/'+p.stem]=p
    known=set(values)|set(files)
    refs={}
    for owner,paths in owners.items():
        text='\n'.join(p.read_text('utf8') for p in paths)
        used={a+'/'+b for a,b in re.findall(r'(?<!android\.)R\.(\w+)\.(\w+)',text)}
        # Includes raw identifiers and dynamic table keys stored in Java string literals.
        used|={'string/'+k for k in re.findall(r'"([A-Za-z_][A-Za-z_0-9]*)"',text) if 'string/'+k in known}
        refs[owner]=used&known
    # Resource names are API constants. Data identifiers and name mapping are shared schema contracts.
    for key in known:
        if key.startswith('string/identity_') or key.startswith('string/label_') or key.startswith('array/identity_'):refs['host'].add(key)
    # Identity -> display-name mapping is a small shared schema used by model validation.
    for _,node in values.get('array/identity_label_keys',[]):
        refs['host'].update('string/'+item.text for item in node if item.text and 'string/'+item.text in known)
    # Module Java is compiled against the host R class. Any direct R.* access
    # therefore carries a host resource ID and must remain in the host table;
    # otherwise the split package contains a second package-local ID that the
    # module code can never resolve (for example course_color). UiText-based
    # strings can still be partitioned normally because they do not appear here.
    for owner,used in refs.items():
        if owner!='host':refs['host'].update(used)
    for p in [root/'app/src/main/AndroidManifest.xml']:
        refs['host']|={a+'/'+b for a,b in re.findall(r'@(\w+)/([\w.]+)',p.read_text('utf8'))}
    contract=root/'modules/host-resource-contract.txt'
    retained=set(contract.read_text('utf8').splitlines()) if contract.exists() else set()
    assert retained<=known, 'Host API 1 resource removed: '+str(sorted(retained-known))
    refs['host']|=retained
    for owner,used in refs.items():
        while True:
            before=len(used)
            for key in list(used):
                data='\n'.join(ET.tostring(n,encoding='unicode') for _,n in values.get(key,[]))
                if key in files and files[key].suffix=='.xml':data+=files[key].read_text('utf8')
                used.update(a+'/'+b for a,b in re.findall(r'@(\w+)/([\w.]+)',data) if a+'/'+b in known)
            if len(used)==before:break
    contract.write_text('\n'.join(sorted(refs['host']))+'\n','utf8')
    for owner,used in refs.items():
        if owner!='host':used-=refs['host']
        rows={}
        for key in sorted(used):
            for qualifier,node in values.get(key,[]):rows.setdefault(qualifier,ET.Element('resources')).append(node)
            if key in files:
                p=files[key];target=stage/owner/p.parent.name/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
        for qualifier,tree in rows.items():
            target=stage/owner/qualifier/'resources.xml';target.parent.mkdir(parents=True,exist_ok=True);ET.indent(tree,space='  ');target.write_text(ET.tostring(tree,encoding='unicode')+'\n','utf8')
        (stage/owner/'values').mkdir(parents=True,exist_ok=True)
    report={owner:{'count':len(keys),'resources':sorted(keys)} for owner,keys in refs.items()}
    (stage.parent/'resource-partition.json').write_text(json.dumps(report,indent=2)+'\n','utf8')
    return stage
