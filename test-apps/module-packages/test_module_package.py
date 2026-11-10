"""Exercise executable module container boundaries against the real signed music payload."""
import copy,json,tempfile,zipfile,warnings
from pathlib import Path
import module_tools as tools

def run():
    count=0
    def check(value,message):
        nonlocal count
        count+=1
        if not value:raise AssertionError(message)
    with tempfile.TemporaryDirectory(prefix='daidai-module-tests-') as temp:
        root=Path(temp);good=root/'music.ddapp';apk=tools.current_apk_path()
        result=tools.pack(apk,good);check(result['signatureVerified'],'real APK signature verified');manifest,payload=tools.validate_container(good);check(payload==apk.read_bytes(),'container retains exact dedicated music APK')
        check(manifest['android']==tools.music_android_contract(),'module version follows current launcher manifest')
        check(result['version']==tools.music_android_contract()['versionName'],'verification reports actual current version')
        check('published' not in manifest and 'githubUploaded' not in result,'offline package checks do not invent publication state')
        with zipfile.ZipFile(good) as z:original={name:z.read(name) for name in z.namelist()}
        def invalid(label,change=None,extra=None):
            entries=dict(original)
            if change:
                m=copy.deepcopy(manifest);change(m);entries['module.json']=json.dumps(m).encode('utf8')
            if extra:extra(entries)
            file=root/(label+'.ddapp')
            with zipfile.ZipFile(file,'w',zipfile.ZIP_DEFLATED) as z:
                for name,data in entries.items():z.writestr(name,data)
            try:tools.validate_container(file)
            except (ValueError,KeyError,UnicodeError,json.JSONDecodeError,zipfile.BadZipFile):check(True,label);return
            raise AssertionError('Accepted '+label)
        for label,change in [
            ('wrong-format',lambda m:m.update(format='other')),
            ('wrong-schema',lambda m:m.update(schemaVersion=2)),
            ('wrong-id',lambda m:m.update(moduleId='weather')),
            ('asset-only',lambda m:m.update(packageType='host-extension-asset')),
            ('not-installable',lambda m:m.update(standaloneInstallable=False)),
            ('wrong-api',lambda m:m.update(hostAdapterApi='unknown/2')),
            ('wrong-host',lambda m:m['requiresHost'].update(api='unknown/1')),
            ('claimed-formal-host',lambda m:m['requiresHost'].update(implementationStatus='released')),
            ('wrong-config-reference',lambda m:m.update(configurationContract='../configuration.json')),
            ('wrong-activity',lambda m:m['android'].update(launchActivity='org.malicious.MainActivity')),
            ('wrong-package',lambda m:m['android'].update(packageName='org.simpleweather.app')),
            ('wrong-version',lambda m:m['android'].update(versionCode=99)),
            ('wrong-minsdk',lambda m:m['android'].update(minSdk=1)),
            ('wrong-signing-pin',lambda m:m['payload'].update(signerCertificateSha256='0'*64)),
            ('wrong-hash',lambda m:m['payload'].update(sha256='0'*64)),
            ('wrong-length',lambda m:m['payload'].update(bytes=len(payload)-1)),
            ('wrong-payload-path',lambda m:m['payload'].update(file='../music.apk')),
        ]:invalid(label,change)
        invalid('truncated-apk',extra=lambda e:e.update({'payload/music.apk':payload[:-1]}))
        invalid('extra-traversal',extra=lambda e:e.update({'../music.apk':b'x'}))
        invalid('extra-windows-path',extra=lambda e:e.update({'C:\\music.apk':b'x'}))
        invalid('missing-payload',extra=lambda e:e.pop('payload/music.apk'))
        invalid('oversized-manifest',extra=lambda e:e.update({'module.json':b'x'*65537}))
        invalid('cross-package-grant-claim',extra=lambda e:e.update({'configuration-contract.json':json.dumps(dict(tools.CONFIG,crossPackageUriGrantsTransferable=True)).encode('utf8')}))
        invalid('private-data-access-claim',extra=lambda e:e.update({'configuration-contract.json':json.dumps(dict(tools.CONFIG,hostCanReadPrivateData=True)).encode('utf8')}))
        duplicate=root/'duplicate.ddapp'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            with zipfile.ZipFile(duplicate,'w') as z:
                for name,data in original.items():z.writestr(name,data)
                z.writestr('module.json',original['module.json'])
        try:tools.validate_container(duplicate)
        except ValueError:check(True,'duplicate entry rejected')
        else:raise AssertionError('Duplicate entry accepted')
        symlink=root/'symlink.ddapp'
        with zipfile.ZipFile(symlink,'w') as z:
            for name,data in original.items():
                info=zipfile.ZipInfo(name)
                if name=='payload/music.apk':info.external_attr=(0o120777<<16)
                z.writestr(info,data)
        try:tools.validate_container(symlink)
        except ValueError:check(True,'symlink entry rejected')
        else:raise AssertionError('Symlink entry accepted')
        check(manifest['requiresHost']['api']=='daidai-toolbox/3.0.0','explicit host requirement')
        check(manifest['requiresHost']['implementationStatus']=='not-yet-implemented','no claim of existing formal host')
    print('PASS: '+str(count)+' executable module package checks')
    return count

if __name__=='__main__':run()
