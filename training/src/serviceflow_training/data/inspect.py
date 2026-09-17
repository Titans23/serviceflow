"""Read article bodies, omitting large applicability menus from console only."""
import argparse
import sys
from serviceflow_training.data.fetch import PLAN,OUT
from serviceflow_training.core.contracts import read_json,require,sha

def main():
    p=argparse.ArgumentParser();p.add_argument('--start',type=int,default=0);p.add_argument('--count',type=int,default=5);a=p.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    for g in read_json(PLAN)['groups'][a.start:a.start+a.count]:
        print('\nGROUP',g['id'],g['split'])
        for page in g['pages']:
            path=OUT/page
            if not (path/'manifest.json').exists():print('PENDING',page);continue
            m=read_json(path/'manifest.json');s=(path/'source.txt').read_text(encoding='utf-8')
            require(sha(path/'source.txt')==m['text_sha256'],'Snapshot drift')
            title=m['title'];first=s.find(title);second=s.find(title,first+len(title))
            body=s[second+len(title):] if second>=0 else s
            body=body.split('是否有帮助?')[0]
            print(page,title,'BODY',len(body));print(body)

if __name__=='__main__':main()
