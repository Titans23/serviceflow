"""Read final artifact identity without starting services or exposing responses."""
import json
from serviceflow_training.core.contracts import ROOT, read_json

def main():
    policy = read_json(ROOT / 'training/configs/final-model.json')
    paths = {name: (ROOT / policy[name]).exists() for name in ['model_directory', 'adapter_directory', 'checkpoint_directory', 'dataset_directory']}
    print(json.dumps({'final_model': policy['id'], 'seed': policy['seed'], 'epoch': policy['epoch'],
                     'task': policy['task'], 'deployment': policy['deployment'],
                     'artifacts_present': paths}, ensure_ascii=False, indent=2))

if __name__ == '__main__': main()
