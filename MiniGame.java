

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.ZipLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.MotionPathListener;
import com.jme3.cinematic.events.MotionTrack;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.WireFrustum;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.BasicShadowRenderer;
import com.jme3.util.SkyFactory;
import com.jme3.util.TangentBinormalGenerator;

/**
 * This is it!
 * @author Alexandra Tsampikakis
 */
public class MiniGame extends SimpleApplication implements AnalogListener, ActionListener {
	
	private boolean changeToCameraView; // True=carView and False=walkingView
	private Sphere sphere;
	
	private BasicShadowRenderer bsr;
	Geometry frustumMdl;
    WireFrustum frustum;

	private Spatial sceneModel;
	private BulletAppState bulletAppState;
	private RigidBodyControl landscape;

	private CharacterControl walkPlayer;
	private Vector3f walkDirection = new Vector3f();
	private boolean left = false, right = false, up = false, down = false;
	
	private VehicleControl carPlayer;
	private Node carNode;
	private float wheelRadius;
	private float steeringValue = 0;
	private float accelerationValue = 0;
	
	float x, y, z, ry, s, xCamCoord, yCamCoord, zCamCoord;
	private Vector3f whereToPlaceNewBox = new Vector3f();
	Node shootables;
	Geometry mark;
	ParticleEmitter fire, debris;
	Spatial wall;
	
	Geometry snow;
	MotionPath path;
	MotionTrack motionControl;
	Node physicsSphere;
	RigidBodyControl ball_phy;
	float snowX, snowY, snowZ, snowScale;
	private Vector3f whereToPlaceSnowBomb = new Vector3f();
	boolean makeWallExplode = false;
	boolean powerUp = false;
	
	Geometry superBall;
	Node superSphere;
	
	public static void main(String[] args) {
		MiniGame app = new MiniGame();
		app.start();
	}

	
	/**
	 * 
	 */
	@Override
	public void simpleInitApp() {
		changeToCameraView = false;
		
		/** Set up Physics */
		bulletAppState = new BulletAppState();
		stateManager.attach(bulletAppState);
		//bulletAppState.getPhysicsSpace().enableDebug(assetManager);
		
		
		viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
		rootNode.attachChild(SkyFactory.createSky(assetManager, "Textures/Sky/Bright/BrightSky.dds", false));
		//rootNode.setShadowMode(ShadowMode.CastAndReceive);
		
		flyCam.setMoveSpeed(10);
		rootNode.setShadowMode(ShadowMode.Off);
		
		if(changeToCameraView == false) {
			walkingCamera();
		} else if(changeToCameraView == true) {
			flyCam.setEnabled(false);
		}
		setUpKeys();
		registerInput();
		setUpLight();
		initCrossHairs();
		initMark();

		PhysicsTestHelper.createPhysicsTestWorld(rootNode, assetManager,bulletAppState.getPhysicsSpace());
		buildPlayer();
		
		
		// We load the scene from the zip file and adjust its size.
		assetManager.registerLocator("town.zip", ZipLocator.class.getName());
		sceneModel = assetManager.loadModel("main.scene");
		sceneModel.setLocalScale(2f);
		sceneModel.setShadowMode(ShadowMode.CastAndReceive);

		// We set up collision detection for the scene by creating a
		// compound collision shape and a static RigidBodyControl with mass
		// zero.
		CollisionShape sceneShape = CollisionShapeFactory.createMeshShape((Node) sceneModel);
		landscape = new RigidBodyControl(sceneShape, 0);
		sceneModel.addControl(landscape);
	    

		// We attach the scene and the player to the rootNode and the physics space,  to make them appear in the game world.
		rootNode.attachChild(sceneModel);
		bulletAppState.getPhysicsSpace().add(landscape);
		bulletAppState.getPhysicsSpace().add(carPlayer);
		bulletAppState.getPhysicsSpace().add(walkPlayer);
		
		shootables = new Node("Shootables");
		rootNode.attachChild(shootables);
		shootables.attachChild(createSuperSphere());
		shootables.setShadowMode(ShadowMode.CastAndReceive);

		// Draw the boxes in my world.
		int random = 0;
		while (random < 40) {
			x = (float) (500 * Math.random()) -100;
			z = (float) (150 * Math.random()) - 30;
			ry = (float) (10 * Math.random()) - 0;
			s = (float) Math.random();
			shootables.attachChild(drawBox());
			random++;
		}
		
		// Shadow.
		bsr = new BasicShadowRenderer(assetManager, 512);
        bsr.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        viewPort.addProcessor(bsr);
        frustum = new WireFrustum(bsr.getPoints());
        frustumMdl = new Geometry("f", frustum);
        frustumMdl.setCullHint(Spatial.CullHint.Never);
        frustumMdl.setShadowMode(ShadowMode.Off);
        frustumMdl.setMaterial(new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md"));
        frustumMdl.getMaterial().getAdditionalRenderState().setWireframe(true);
        frustumMdl.getMaterial().setColor("Color", ColorRGBA.Red);
        rootNode.attachChild(frustumMdl);
	}
	
	
	/**
	 * Setup the camera when outside the car.
	 */
	public void walkingCamera() {
		CapsuleCollisionShape capsuleShape = new CapsuleCollisionShape(1.5f, 6f, 1);
	    walkPlayer = new CharacterControl(capsuleShape, 0.05f);
	    walkPlayer.setJumpSpeed(20);
	    walkPlayer.setFallSpeed(30);
	    walkPlayer.setGravity(30);
		walkPlayer.setPhysicsLocation(new Vector3f(0, 20, 0));
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        final BitmapText wayPointsText = new BitmapText(guiFont, false);
        wayPointsText.setSize(guiFont.getCharSet().getRenderedSize());
        guiNode.attachChild(wayPointsText);
	}
	
	
	
	/**
	 * Setup the light in my world.
	 */
	private void setUpLight() {		
		DirectionalLight sun = new DirectionalLight();
		sun.setColor(ColorRGBA.White);
		sun.setDirection(new Vector3f(-.5f,-.5f,-.5f).normalizeLocal());
		rootNode.addLight(sun);
	}
	
	
	
	/**
	 * Used for the car.
	 * @param spatial
	 * @param name
	 * @return
	 */
	private Geometry findGeom(Spatial spatial, String name) {
		if (spatial instanceof Node) {
			Node node = (Node) spatial;
			for(int i = 0; i < node.getQuantity(); i++) {
				Spatial child = node.getChild(i);
				Geometry result = findGeom(child, name);
				if (result != null) {
					return result;
				}
			}
		} else if (spatial instanceof Geometry) {
			if (spatial.getName().startsWith(name)) {
				return (Geometry) spatial;
			}
		}
		return null;
	}

	
	/**
	 * Build the car, the player controls the car.
	 */
	private void buildPlayer() {
		float stiffness = 120.0f;// 200=f1 car
		float compValue = 0.2f; // (lower than damp!)
		float dampValue = 0.3f;
		final float mass = 400;

		// Load model and get chassis Geometry
		carNode = (Node) assetManager.loadModel("Models/Ferrari/Car.scene");
		carNode.setShadowMode(ShadowMode.Cast);
		Geometry chasis = findGeom(carNode, "Car");
		BoundingBox box = (BoundingBox) chasis.getModelBound();

		// Create a hull collision shape for the chassis
		CollisionShape carHull = CollisionShapeFactory.createDynamicMeshShape(chasis);

		// Create a vehicle control
		carPlayer = new VehicleControl(carHull, mass);
		carNode.addControl(carPlayer);

		// Setting default values for wheels
		carPlayer.setSuspensionCompression(compValue * 2.0f * FastMath.sqrt(stiffness));
		carPlayer.setSuspensionDamping(dampValue * 2.0f * FastMath.sqrt(stiffness));
		carPlayer.setSuspensionStiffness(stiffness);
		carPlayer.setMaxSuspensionForce(10000);

		// Create four wheels and add them at their locations
		Vector3f wheelDirection = new Vector3f(0, -1, 0);
		Vector3f wheelAxle = new Vector3f(-1, 0, 0);

		Geometry wheel_fr = findGeom(carNode, "WheelFrontRight");
		wheel_fr.center();
		box = (BoundingBox) wheel_fr.getModelBound();
		wheelRadius = box.getYExtent();
		float back_wheel_h = (wheelRadius * 1.7f) - 1f;
		float front_wheel_h = (wheelRadius * 1.9f) - 1f;
		carPlayer.addWheel(wheel_fr.getParent(), box.getCenter().add(0, -front_wheel_h, 0), wheelDirection, wheelAxle, 0.2f, wheelRadius, true);

		Geometry wheel_fl = findGeom(carNode, "WheelFrontLeft");
		wheel_fl.center();
		box = (BoundingBox) wheel_fl.getModelBound();
		carPlayer.addWheel(wheel_fl.getParent(), box.getCenter().add(0, -front_wheel_h, 0), wheelDirection, wheelAxle, 0.2f, wheelRadius, true);

		Geometry wheel_br = findGeom(carNode, "WheelBackRight");
		wheel_br.center();
		box = (BoundingBox) wheel_br.getModelBound();
		carPlayer.addWheel(wheel_br.getParent(), box.getCenter().add(0, -back_wheel_h, 0), wheelDirection, wheelAxle, 0.2f, wheelRadius, false);

		Geometry wheel_bl = findGeom(carNode, "WheelBackLeft");
		wheel_bl.center();
		box = (BoundingBox) wheel_bl.getModelBound();
		carPlayer.addWheel(wheel_bl.getParent(), box.getCenter().add(0, -back_wheel_h, 0), wheelDirection, wheelAxle, 0.2f, wheelRadius, false);

		carPlayer.getWheel(2).setFrictionSlip(4);
		carPlayer.getWheel(3).setFrictionSlip(4);
		carPlayer.setPhysicsLocation(new Vector3f(2, 10, 2));
		rootNode.attachChild(carNode);

		carNode.setShadowMode(ShadowMode.CastAndReceive);
	}
	
	
	/**
	 * Draw a box.
	 */
	public Spatial drawBox() {
		Box box = new Box(Vector3f.ZERO, 3.5f, 3.5f, 1.0f);
		wall = new Geometry("Box", box);
		Material mat_brick = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat_brick.setTexture("ColorMap", assetManager.loadTexture("Textures/Terrain/BrickWall/BrickWall.jpg"));
		wall.setMaterial(mat_brick);
		wall.setLocalTranslation(x, 0, z);
		wall.scale(s);
		wall.rotate( 0, ry, 0);
		wall.setShadowMode(ShadowMode.CastAndReceive);
		return wall;
	}
	
	
	/**
	 * Setting fire.
	 */
	public void setFire() {
		fire = new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 30);
		Material mat_red = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
		mat_red.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
		fire.setMaterial(mat_red);
		fire.setImagesX(2);
		fire.setImagesY(2); // 2x2 texture animation
		fire.setEndColor(new ColorRGBA(1f, 0f, 0f, 1f)); // red
		fire.setStartColor(new ColorRGBA(1f, 1f, 0f, 0.5f)); // yellow
		fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2, 0));
		fire.setStartSize(1.5f);
		fire.setEndSize(0.1f);
		fire.setGravity(0, 0, 0);
		fire.setLowLife(1f);
		fire.setHighLife(3f);
		fire.getParticleInfluencer().setVelocityVariation(0.3f);
		fire.setShadowMode(ShadowMode.CastAndReceive);
		rootNode.attachChild(fire);

		debris = new ParticleEmitter("Debris", ParticleMesh.Type.Triangle, 10);
		Material debris_mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
		debris_mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/Debris.png"));
		debris.setMaterial(debris_mat);
		debris.setImagesX(3);
		debris.setImagesY(3); // 3x3 texture animation
		debris.setRotateSpeed(4);
		debris.setSelectRandomImage(true);
		debris.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 4, 0));
		debris.setStartColor(ColorRGBA.White);
		debris.setGravity(0, 6, 0);
		debris.getParticleInfluencer().setVelocityVariation(.60f);
		rootNode.attachChild(debris);
		debris.setShadowMode(ShadowMode.CastAndReceive);
		debris.emitAllParticles();
	}
	
	
	/** 
	 * Register the user input from keyboard.
	 */
	public void registerInput() {
	    inputManager.addMapping("moveForward", new KeyTrigger(keyInput.KEY_W));
	    inputManager.addMapping("moveBackward", new KeyTrigger(keyInput.KEY_S));
	    inputManager.addMapping("moveRight", new KeyTrigger(keyInput.KEY_D));
	    inputManager.addMapping("moveLeft", new KeyTrigger(keyInput.KEY_A));
	    inputManager.addMapping("changeView", new KeyTrigger(keyInput.KEY_TAB));
	    inputManager.addListener(this, "moveForward", "moveBackward", "moveRight", "moveLeft", "changeView");
	  }
	
	
	/**
	 * We over-write some navigational key mappings here, so we can add
	 * physics-controlled walking and jumping:
	 */
	private void setUpKeys() {
		inputManager.addMapping("Lefts", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Rights", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Ups", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Downs", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Reset", new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Place new box", new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("Start snoing", new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("explode", new KeyTrigger(KeyInput.KEY_R));
		inputManager.addListener(this, "Lefts");
        inputManager.addListener(this, "Rights");
        inputManager.addListener(this, "Ups");
        inputManager.addListener(this, "Downs");
        inputManager.addListener(this, "Reset");
        inputManager.addListener(this, "Jump");
        inputManager.addListener(this, "Place new box");
        inputManager.addListener(this, "Start snoing");
        inputManager.addListener(this, "explode");
     
        inputManager.addMapping("Shoot", new KeyTrigger(KeyInput.KEY_SPACE), new MouseButtonTrigger(MouseInput.BUTTON_LEFT)); 
    	inputManager.addListener(actionListener, "Shoot");
	}

	
	/**
	 * 
	 */
	private ActionListener actionListener = new ActionListener() {
		
		public void onAction(String name, boolean keyPressed, float tpf) {
			if (name.equals("Shoot") && !keyPressed && changeToCameraView==false) {
				CollisionResults results = new CollisionResults(); // 1. Reset results list.
				Ray ray = new Ray(cam.getLocation(), cam.getDirection()); // 2. Aim the ray from cam loc to cam direction.
				shootables.collideWith(ray, results); // 3. Collect intersections between Ray and Shootables in results list.
								
				System.out.println("----- Collisions? " + results.size() + "-----"); // 4. Print the results
				for (int i = 0; i < results.size(); i++) {
					float dist = results.getCollision(i).getDistance(); // For each hit, we know distance, impact point, name of geometry.
					Vector3f pt = results.getCollision(i).getContactPoint();
					String hit = results.getCollision(i).getGeometry().getName();
					
					
					if(hit == "Box") {
						if(makeWallExplode && powerUp) {
							shootables.detachChild(results.getCollision(i).getGeometry());
						} else {
							setFire();
							fire.move(pt);
							debris.move(pt);
						}
					}
					
					if(hit == "Ball") {
						shootables.detachChild(results.getCollision(i).getGeometry());
						powerUp =  true;
					}
					
					
					System.out.println("* Collision #" + i);
					System.out.println("  You shot " + hit + " at " + pt + ", " + dist + " wu away.");
				}
				if (results.size() > 0) { // 5. Use the results (we mark the hit object)
					CollisionResult closest = results.getClosestCollision(); // The closest collision point is what was truly hit.
			
					mark.setLocalTranslation(closest.getContactPoint()); // Let's interact - we mark the hit with a red dot.
					rootNode.attachChild(mark);
				} else {
					rootNode.detachChild(mark); // No hits? Then remove the red mark.
				}
			}
		}
		
	};
	
	/**
	 * 
	 */
	@Override
	public void onAnalog(String binding, float value, float tpf) {
		
	}
	
	
	/**
	 * A red ball that marks the last spot that was "hit" by the "shot".
	 */
	protected void initMark() {
		Sphere sphere = new Sphere(30, 30, 0.2f);
		mark = new Geometry("BOOM!", sphere);
		Material mark_mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mark_mat.setColor("Color", ColorRGBA.Red);
		mark.setMaterial(mark_mat);
	}
	
	
	/**
	 * A centred plus sign to help the player aim.
	 */
	protected void initCrossHairs() {
		guiNode.detachAllChildren();
		guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
		BitmapText ch = new BitmapText(guiFont, false);
		ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
		ch.setText("+"); // crosshairs
		xCamCoord = settings.getWidth() / 2 - guiFont.getCharSet().getRenderedSize() / 3 * 2;
		yCamCoord = settings.getHeight() / 2 + ch.getLineHeight() / 2;
		zCamCoord = 0;
		ch.setLocalTranslation(xCamCoord, yCamCoord, zCamCoord);
		guiNode.attachChild(ch);
		
		if(changeToCameraView == true) { // Hide the cross if we are in the car.
			guiNode.detachChild(ch);
		}
	}


		/**
		 * This is what happens when pressing a key on the keyboard.
		 */
		@Override
		public void onAction(String binding, boolean value, float tpf) {
			if(changeToCameraView == true) {
				if(binding.equals("Lefts")) {
					if(value) {
						steeringValue += .5f;
					} else {
						steeringValue += -.5f;
					}
					carPlayer.steer(steeringValue);
				} else if (binding.equals("Rights")) {
					if(value) {
						steeringValue += -.5f;
					} else {
						steeringValue += .5f;
					}
					carPlayer.steer(steeringValue);
				} else if(binding.equals("Ups")) {
					if(value) {
						accelerationValue -= 800;
					} else {
						accelerationValue += 800;
					}
					carPlayer.accelerate(accelerationValue);
					carPlayer.setCollisionShape(CollisionShapeFactory.createDynamicMeshShape(findGeom(carNode, "Car")));
				} else if (binding.equals("Downs")) {
					if(value) {
						carPlayer.brake(4000f);
					} else {
						carPlayer.brake(0f);
					}
				} else if(binding.equals("Reset")) {
					if (value) {
						if(changeToCameraView == true) {
							carPlayer.setPhysicsLocation(new Vector3f(2, 10, 2));
							carPlayer.setPhysicsRotation(new Matrix3f());
							carPlayer.setLinearVelocity(Vector3f.ZERO);
							carPlayer.setAngularVelocity(Vector3f.ZERO);
							carPlayer.resetSuspension();
						}
					} else {
						
					}
				} else if(binding.equals("Place new box")) {
					if(value) {
						ry = (float) (10 * Math.random()) - 0;
						s = (float) Math.random();
						Box box = new Box(Vector3f.ZERO, 3.5f, 3.5f, 1.0f);
						wall = new Geometry("Box", box);
						Material mat_brick = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
						mat_brick.setTexture("ColorMap", assetManager.loadTexture("Textures/Terrain/BrickWall/BrickWall.jpg"));
						wall.setMaterial(mat_brick);
						whereToPlaceNewBox = carPlayer.getPhysicsLocation();
						wall.setLocalTranslation(whereToPlaceNewBox);
						wall.scale(s);
						wall.rotate( 0, ry, 0);
						wall.setShadowMode(ShadowMode.CastAndReceive);
						shootables.attachChild(wall);
					} else {
						
					}
				} else if(binding.equals("Start snoing")) {
					if(value) {
						startSnow();
					} else {
						
					}
				}
			}
			
			if(changeToCameraView == false) {
				if (binding.equals("Left")) {
					if (value) { left = true; } else { left = false; }
				} else if (binding.equals("Right")) {
					if (value) { right = true; } else { right = false; }
				} else if (binding.equals("Up")) {
					if (value) { up = true; } else { up = false; }
				} else if (binding.equals("Down")) {
					if (value) { down = true; } else { down = false; }
				} else if (binding.equals("Jump")) {
				      walkPlayer.jump();
			    } else if(binding.equals("explode")) {
					if(value) {
						System.out.println(powerUp);
						if(powerUp) {
							makeWallExplode = !makeWallExplode;
						}
					} else {
						
					}
				}
			}
			
			if(binding.equals("changeView")) {
				initCrossHairs();
					if(value) {
						if(changeToCameraView == false) {
							carPlayer.setPhysicsRotation(new Matrix3f());
							carPlayer.setLinearVelocity(Vector3f.ZERO);
							carPlayer.setAngularVelocity(Vector3f.ZERO);
							carPlayer.resetSuspension();
							changeToCameraView=true;
						} else {
							changeToCameraView = false;
						}
					}
			}
		}
		
		/**
		 * 
		 */
		public void startSnow() {
			int howManyShowBalls = 0;
			whereToPlaceSnowBomb = carPlayer.getPhysicsLocation();
			
			while(howManyShowBalls < 10) {
				snowX = (float) ((whereToPlaceSnowBomb.x + 10) * Math.random()) - (whereToPlaceSnowBomb.x);
			    snowY = (float) ((whereToPlaceSnowBomb.y + 30) * Math.random()) - (whereToPlaceSnowBomb.y);
			    snowZ = (float) ((whereToPlaceSnowBomb.z + 10) * Math.random()) - (whereToPlaceSnowBomb.z);
				createSnow();
				howManyShowBalls++;
			}
		}
		
		
		
		/**
		 * Create snow with stone texture.
		 */
		public void createSnow() {
		    //Create sphere
			sphere = new Sphere(32,32, 0.5f);
			snow = new Geometry("Snow", sphere);
		    sphere.setTextureMode(Sphere.TextureMode.Projected); // better quality on spheres
		    TangentBinormalGenerator.generate(sphere);           // for lighting effect
		    Material mat_lit = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
		    mat_lit.setTexture("DiffuseMap", assetManager.loadTexture("Textures/ColoredTex/snow.jpg"));
		    mat_lit.setTexture("NormalMap", assetManager.loadTexture("Textures/Terrain/Pond/Pond_normal.png"));
		    mat_lit.setBoolean("UseMaterialColors",true);    
		    mat_lit.setColor("Specular",ColorRGBA.White);
		    mat_lit.setColor("Diffuse",ColorRGBA.White);
		    mat_lit.setFloat("Shininess", 5f); // [1,128]    
		    snow.setMaterial(mat_lit);
		    snow.rotate(1.6f, 0, 0);
		    rootNode.attachChild(snow);
		    snow.setShadowMode(ShadowMode.CastAndReceive);
		    
		    //Motion path
		    path = new MotionPath();
	        path.addWayPoint(new Vector3f(snowX, snowY, snowZ));
	        path.addWayPoint(new Vector3f(snowX, 0.5f, snowZ));
	        path.enableDebugShape(assetManager, rootNode);
	        path.disableDebugShape();
	        motionControl = new MotionTrack(snow, path);
	        motionControl.setDirectionType(MotionTrack.Direction.PathAndRotation);
	        motionControl.setInitialDuration(10f);
	        motionControl.setSpeed(1f);
	        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
	        final BitmapText wayPointsText = new BitmapText(guiFont, false);
	        wayPointsText.setSize(guiFont.getCharSet().getRenderedSize());
	        guiNode.attachChild(wayPointsText);
	        
	        path.addListener(new MotionPathListener() {
	        	
	            public void onWayPointReach(MotionTrack control, int wayPointIndex) {
	                if (path.getNbWayPoints() == wayPointIndex + 1) {
	                    wayPointsText.setText(control.getSpatial().getName() + "Finished!!! ");
	                } else {
	                    wayPointsText.setText(control.getSpatial().getName() + " Reached way point " + wayPointIndex);
	                }
	                wayPointsText.setLocalTranslation((cam.getWidth() - wayPointsText.getLineWidth()) / 2, cam.getHeight(), 0);
	            }
	        });
		    
		    motionControl.play();
		}
		
		
		/**
		 * Creates a sphere with the power to delete boxes.
		 */
		public Spatial createSuperSphere() {
			sphere = new Sphere(32,32, 0.5f);
			superBall = new Geometry("Ball", sphere);
		    sphere.setTextureMode(Sphere.TextureMode.Projected); // better quality on spheres
		    TangentBinormalGenerator.generate(sphere);           // for lighting effect
		    Material mat_lit = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
		    mat_lit.setTexture("DiffuseMap", assetManager.loadTexture("Textures/ColoredTex/snow.jpg"));
		    mat_lit.setTexture("NormalMap", assetManager.loadTexture("Textures/Terrain/Pond/Pond_normal.png"));
		    mat_lit.setBoolean("UseMaterialColors",true);    
		    mat_lit.setColor("Specular",ColorRGBA.White);
		    mat_lit.setColor("Diffuse",ColorRGBA.White);
		    mat_lit.setFloat("Shininess", 5f); // [1,128]    
		    superBall.setMaterial(mat_lit);
		    superBall.rotate(1.6f, 0, 0);
		    superBall.setLocalTranslation(50, 1, 20);
		    shootables.setShadowMode(ShadowMode.CastAndReceive);
		    return superBall;
		}
		
		
		/**
		 * Set the camera to follow the car if we are in the car.
		 */
		@Override
		public void simpleUpdate(float tpf) {
		    
			if(changeToCameraView == true) {
				this.cam.setLocation( carNode.localToWorld( new Vector3f( 0, 3 /* units above car*/, 10 /* units behind car*/ ), null));
		        this.cam.lookAt(this.carNode.getWorldTranslation(), Vector3f.UNIT_Y);
			}
		}
		
	}
	


