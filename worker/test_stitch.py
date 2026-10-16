import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import cv2
import numpy as np
from scipy.spatial.transform import Rotation
from stitch import stitch, load_frames, refine_rotations, RetakeRequired, sphere_rays, rectified_pair, pair_rotation

def fixture(folder, *, pattern=None, image_width=320, image_height=320, focal=134., roll_degrees=0.):
    folder.mkdir(parents=True,exist_ok=True)
    rng=np.random.default_rng(19)
    texture=rng.integers(20,235,(512,1024,3),dtype=np.uint8)
    texture=cv2.GaussianBlur(texture,(5,5),0)
    for _ in range(300):
        x,y=rng.integers(0,1024),rng.integers(0,512)
        cv2.circle(texture,(int(x),int(y)),int(rng.integers(3,16)),tuple(int(v) for v in rng.integers(0,255,3)),-1)
    frames=[]
    yy,xx=np.mgrid[:image_height,:image_width];local=np.stack(((xx-image_width/2)/focal,-(yy-image_height/2)/focal,-np.ones_like(xx)),axis=-1)
    local/=np.linalg.norm(local,axis=-1)[:,:,None]
    directions=pattern or [(yaw,pitch) for pitch in [-60,0,60] for yaw in range(-180,180,45)]
    for yaw,pitch in directions:
        # Compose world yaw after local pitch, keeping camera vertical.
        rotation=Rotation.from_euler("y",-yaw,degrees=True).as_matrix()@Rotation.from_euler("x",pitch,degrees=True).as_matrix()
        rotation=rotation@Rotation.from_euler("z",roll_degrees,degrees=True).as_matrix()
        rays=local@rotation.T
        u=(np.arctan2(rays[:,:,0],-rays[:,:,2])+np.pi)/(2*np.pi)*1024-.5
        v=(np.pi/2-np.arcsin(rays[:,:,1]))/np.pi*512-.5
        image=cv2.remap(texture,u.astype(np.float32),v.astype(np.float32),cv2.INTER_LINEAR,borderMode=cv2.BORDER_WRAP)
        identifier=str(len(frames));path=folder/f"{identifier}.jpg";cv2.imwrite(str(path),image,[cv2.IMWRITE_JPEG_QUALITY,98])
        pose=np.eye(4);pose[:3,:3]=rotation
        frames.append({"id":identifier,"width":image_width,"height":image_height,"intrinsics":[focal,focal,image_width/2,image_height/2],"pose":pose.flatten(order="F").tolist(),"sha256":hashlib.sha256(path.read_bytes()).hexdigest()})
    return {"version":1,"frames":frames}

class StitchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.folder=Path(cls.temp.name);cls.manifest=fixture(cls.folder)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def test_full_sphere_from_calibrated_photos(self):
        quality=stitch(self.manifest,self.folder,self.folder/"out.jpg",width=512)
        self.assertGreater(quality["coverage"],.995)
        self.assertGreater(quality["matchedPairs"],20)
        self.assertEqual(cv2.imread(str(self.folder/"out.jpg")).shape,(256,512,3))
    def test_twelve_ultrawide_photos_cover_full_sphere(self):
        pattern=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        folder=self.folder/"ultrawide"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=156.)
        self.assertEqual(len(manifest["frames"]),12)
        quality=stitch(manifest,folder,folder/"out.jpg",width=512)
        self.assertGreater(quality["coverage"],.995)
    def test_multiple_wrong_recorded_directions_are_recovered_from_photos(self):
        pattern=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        folder=self.folder/"wrong-pose"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=156.)
        # The photos still see their true directions, but two recorded poses
        # point at unrelated targets.
        manifest["frames"][5]["pose"]=manifest["frames"][0]["pose"].copy()
        manifest["frames"][9]["pose"]=manifest["frames"][3]["pose"].copy()
        images,cameras,recorded=load_frames(manifest,folder)
        recovered,_=refine_rotations(images,cameras,recorded)
        expected=Rotation.from_euler("y",-300,degrees=True).as_matrix()
        error=np.rad2deg(Rotation.from_matrix(recovered[5].T@expected).magnitude())
        self.assertLess(error,3)
        expected_floor=Rotation.from_euler("y",-60,degrees=True).as_matrix()@Rotation.from_euler("x",-60,degrees=True).as_matrix()
        floor_error=np.rad2deg(Rotation.from_matrix(recovered[9].T@expected_floor).magnitude())
        self.assertLess(floor_error,3)
        quality=stitch(manifest,folder,folder/"recovered.jpg",width=512)
        self.assertGreater(quality["largestPoseCorrectionDegrees"],45)
        self.assertGreater(quality["coverage"],.995)
    def test_duplicate_photo_cannot_fake_full_coverage(self):
        pattern=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        folder=self.folder/"duplicate-photo"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=156.)
        duplicate=(folder/"0.jpg").read_bytes()
        (folder/"5.jpg").write_bytes(duplicate)
        manifest["frames"][5]["sha256"]=hashlib.sha256(duplicate).hexdigest()
        with self.assertRaisesRegex(RetakeRequired,"Photo 6 repeats nearly the same view as photo 1"):
            stitch(manifest,folder,folder/"invalid.jpg",width=512)
    def test_featureless_ceiling_uses_recorded_pose(self):
        pattern=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        folder=self.folder/"plain-ceiling"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=156.)
        image=np.full((300,400,3),165,dtype=np.uint8)
        path=folder/"6.jpg";cv2.imwrite(str(path),image)
        manifest["frames"][6]["sha256"]=hashlib.sha256(path.read_bytes()).hexdigest()
        quality=stitch(manifest,folder,folder/"out.jpg",width=512)
        self.assertEqual(quality["poseOnlyFrames"],[7])
        self.assertGreater(quality["coverage"],.995)
    def test_twelve_ultrawide_photos_tolerate_five_degree_aim_error(self):
        pattern=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        rng=np.random.default_rng(42)
        pattern=[(yaw+float(rng.uniform(-5,5)),pitch+float(rng.uniform(-5,5))) for yaw,pitch in pattern]
        folder=self.folder/"ultrawide-offset"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=156.)
        quality=stitch(manifest,folder,folder/"out.jpg",width=512,refine=False)
        self.assertGreater(quality["coverage"],.995)
    def test_twelve_target_layout_covers_sphere_with_aim_variation(self):
        centers=[(yaw,0) for yaw in range(0,360,60)]+[(yaw,60) for yaw in range(0,360,120)]+[(yaw,-60) for yaw in range(60,360,120)]
        rays=sphere_rays(360,180)
        rng=np.random.default_rng(122)
        # 400×300 with focal length 156 reproduces the S22 ultrawide's
        # 104° horizontal / 88° vertical field of view.
        for _ in range(100):
            covered=np.zeros(rays.shape[:2],dtype=bool)
            for center_yaw,center_pitch in centers:
                yaw=center_yaw+rng.uniform(-5,5)
                pitch=center_pitch+rng.uniform(-5,5)
                rotation=Rotation.from_euler("y",-yaw,degrees=True).as_matrix()@Rotation.from_euler("x",pitch,degrees=True).as_matrix()
                local=rays@rotation
                depth=-local[:,:,2]
                covered|=(depth>0)&(np.abs(local[:,:,0])<depth*200/156)&(np.abs(local[:,:,1])<depth*150/156)
            self.assertGreater(np.mean(covered),.999)
    def test_checksum_failure(self):
        manifest=copy.deepcopy(self.manifest);manifest["frames"][0]["sha256"]="0"*64
        with self.assertRaisesRegex(ValueError,"checksum"):load_frames(manifest,self.folder)
    def test_missing_ceiling_and_floor_is_rejected(self):
        manifest={"frames":self.manifest["frames"][8:16]}
        with self.assertRaisesRegex(RetakeRequired,"Coverage"):stitch(manifest,self.folder,self.folder/"invalid.jpg",width=512,refine=False)
    def test_translation_is_rejected(self):
        manifest=copy.deepcopy(self.manifest);manifest["frames"][0]["pose"][12]=1.
        with self.assertRaisesRegex(RetakeRequired,"moved too far"):load_frames(manifest,self.folder)
    def test_flattened_pose_cannot_hide_observed_camera_movement(self):
        manifest=copy.deepcopy(self.manifest)
        for frame in manifest["frames"]: frame["observedPose"]=frame["pose"].copy()
        manifest["frames"][0]["observedPose"][12]=.74
        with self.assertRaisesRegex(RetakeRequired,"74 cm"):
            load_frames(manifest,self.folder)
    def test_small_centering_variation_is_accepted(self):
        manifest=copy.deepcopy(self.manifest)
        for i,frame in enumerate(manifest["frames"]):frame["pose"][12]=.1 if i%2 else -.1
        load_frames(manifest,self.folder)
    def test_shared_projection_recovers_wide_angle_overlap(self):
        images,cameras,rotations=load_frames(self.manifest,self.folder)
        result=rectified_pair(images,cameras,rotations,8,9)
        self.assertIsNotNone(result)
        relative=result[2]
        error=np.degrees(Rotation.from_matrix(relative.T@rotations[8].T@rotations[9]).magnitude())
        self.assertLess(error,2)
    def test_repeated_matches_to_one_pixel_cannot_define_a_camera_rotation(self):
        points=np.column_stack((np.arange(30)*3,np.arange(30)*2)).astype(np.float32)
        repeated=np.full_like(points,50.)
        rays=np.tile([0.,0.,-1.],(30,1))
        self.assertIsNone(pair_rotation(points,repeated,rays,rays))
    def test_fifteen_portrait_views_cover_and_align_the_sphere(self):
        pattern=[(index*360/7,0) for index in range(7)]+[(yaw,55) for yaw in range(0,360,90)]+[(yaw,-55) for yaw in range(45,360,90)]
        folder=self.folder/"portrait-overlap"
        manifest=fixture(folder,pattern=pattern,image_width=400,image_height=300,focal=164.2,roll_degrees=-90)
        quality=stitch(manifest,folder,folder/"out.jpg",width=512)
        self.assertGreater(quality["coverage"],.995)
        self.assertGreater(quality["matchedPairs"],15)
    def test_bad_rotation_is_rejected(self):
        manifest=copy.deepcopy(self.manifest);manifest["frames"][0]["pose"][0]=20
        with self.assertRaisesRegex(ValueError,"rotation"):load_frames(manifest,self.folder)
    def test_equirectangular_coordinate_convention(self):
        rays=sphere_rays(360,180)
        self.assertTrue(np.allclose(rays[90,180],[0,0,-1],atol=.01))
        self.assertTrue(np.allclose(rays[90,270],[1,0,0],atol=.01))
        self.assertGreater(rays[0,180,1],.99)

if __name__=="__main__":unittest.main()
