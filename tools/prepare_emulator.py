"""Optional Android 15 test device, contained in the project's E: workspace."""
import concurrent.futures
import hashlib
import pathlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
SDK=ROOT/'.tools'/'android-sdk'
CACHE=ROOT/'.cache'/'downloads'


def prepare(spec):
    name,url,expected,destination=spec
    archive=CACHE/name
    CACHE.mkdir(parents=True,exist_ok=True)
    if not destination.exists():
        if not archive.exists():
            print('Downloading',name,flush=True)
            with urllib.request.urlopen(url,timeout=45) as src,archive.open('wb') as dst:
                total=0;reported=0
                while chunk:=src.read(1024*1024):
                    dst.write(chunk);total+=len(chunk)
                    if total-reported>=256*1024*1024:
                        print(name,total//1024//1024,'MB',flush=True);reported=total
        digest=hashlib.sha1()
        with archive.open('rb') as f:
            while b:=f.read(1024*1024):digest.update(b)
        if digest.hexdigest()!=expected:raise RuntimeError('Package checksum mismatch: '+name)
        staging=SDK/('unpack-'+name)
        staging.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(archive) as z:z.extractall(staging)
        children=list(staging.iterdir())
        if len(children)!=1 or not children[0].is_dir():raise RuntimeError('Unexpected SDK package layout')
        destination.parent.mkdir(parents=True,exist_ok=True)
        children[0].rename(destination);staging.rmdir()
    print('Ready',destination,flush=True)


if __name__=='__main__':
    repo=ET.fromstring((ROOT/'.cache/reference/android-repository.xml').read_bytes())
    image=ET.fromstring((ROOT/'.cache/reference/android-system-images.xml').read_bytes())
    specs=[]
    for p in repo.findall('remotePackage'):
        if p.attrib['path']=='emulator' and p.find('channelRef').attrib['ref']=='channel-0':
            a=next(a for a in p.findall('./archives/archive') if a.findtext('host-os')=='windows')
            c=a.find('complete');n=c.findtext('url')
            specs.append((n,'https://dl.google.com/android/repository/'+n,c.findtext('checksum'),SDK/'emulator'))
    for p in image.findall('remotePackage'):
        if p.attrib['path']=='system-images;android-35;google_apis;x86_64':
            c=p.find('./archives/archive/complete');n=c.findtext('url')
            specs.append((n,'https://dl.google.com/android/repository/sys-img/google_apis/'+n,c.findtext('checksum'),SDK/'system-images/android-35/google_apis/x86_64'))
    if len(specs)!=2:raise RuntimeError('Emulator packages not found')
    with concurrent.futures.ThreadPoolExecutor(2) as pool:list(pool.map(prepare,specs))
