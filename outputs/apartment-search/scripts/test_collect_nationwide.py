import importlib.util
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('collect',Path(__file__).with_name('collect-nationwide.py'))
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
class CollectionTest(unittest.TestCase):
 def test_month_boundary_and_invalid_range(self):
  self.assertEqual(c.months('202512','202601'),['202512','202601'])
  with self.assertRaises(ValueError):c.months('202609','202607')
 def test_resume_failed_and_interrupted_but_skip_success_and_review(self):
  rows=[{'code':'11110','status':'ready'},{'code':'12110','status':'review'}]
  state={'11110/202607':{'status':'success'},'11110/202608':{'status':'failed'},'11110/202609':{'status':'running'}}
  self.assertEqual([m for _,m in c.pending(rows,['202607','202608','202609'],state)],['202608','202609'])
 def test_success_requires_correct_segment_and_exit(self):
  log='MOLIT 성공: 지역 11110, 계약월 2026-07, 저장 0건, 해제 제외 2건'
  self.assertEqual(c.outcome('11110','202607',0,log)['saved'],0)
  self.assertEqual(c.outcome('11110','202608',0,log)['status'],'failed')
  self.assertEqual(c.outcome('11110','202607',1,log)['status'],'failed')
 def test_duplicate_catalog_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'r.tsv';p.write_text('code\tprovince\tdistrict\tstatus\n11110\t서울\t종로구\tready\n11110\t서울\t종로구\tready\n')
   with self.assertRaises(ValueError):c.catalog(p)
 def test_atomic_state_roundtrip(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'state.json';c.save(p,{'segments':{}})
   self.assertEqual(c.json.loads(p.read_text()),{'segments':{}})
   self.assertFalse(p.with_suffix('.tmp').exists())
if __name__=='__main__':unittest.main()
