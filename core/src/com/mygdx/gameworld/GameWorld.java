package com.mygdx.gameworld;


import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.mygdx.config.Configuration;
import com.mygdx.game_objects.Bullet;
import com.mygdx.game_objects.Enemy;
import com.mygdx.game_objects.collect_items.Gem;
import com.mygdx.game_objects.map.GameMap;
import com.mygdx.game_objects.Robot;
import com.mygdx.game_objects.robots.GemBot;
import com.mygdx.game_objects.robots.GunnerBot;
import com.mygdx.gui_objects.HudPanel;
import com.mygdx.gui_objects.RobotTile;
import com.mygdx.gui_objects.SelectRobotPanel;
import com.mygdx.lang_helpers.ArrayListHelpers;
import com.mygdx.lang_helpers.Predicate;
import com.mygdx.level_infrastructure.Level;
import com.mygdx.level_infrastructure.LevelFactory;

import java.util.*;
import java.util.List;

public class GameWorld {
    private TreeSet<Enemy> readyEnemies;
    private GameMap map;
    private SelectRobotPanel selectRobotPanel;
    private ArrayList<Class> availableRobots;
    private Robot selectedRobot;
    private RobotTile selectedRobotTile;
    private PointerActions action;
    private Rectangle gameField;
    private Rectangle robotBarField;
    private Rectangle statusBarField;
    private float gameTime;
    private ArrayList<Bullet> bullets;
    private GameState gameState;
    private int gemsCount;
    private int lives;
    private HudPanel hud;
    private ArrayList<Gem> gems;

    private ArrayListHelpers<Bullet> bulletArrayListHelpers;
    private ArrayListHelpers<Robot> robotArrayListHelpers;
    private ArrayListHelpers<Enemy> enemyArrayListHelpers;
    private ArrayListHelpers<Gem> gemArrayListHelpers;

    public GameWorld(int levelNumber) {
        gameTime = 0;
        Level level = LevelFactory.createLevel(levelNumber);

        // Initialize all level enemies
        readyEnemies = new TreeSet<Enemy>(new Comparator<Enemy>() {
            @Override
            public int compare(Enemy enemy, Enemy t1) {
                if (enemy.getSpawnTime() < t1.getSpawnTime()) {
                    return -1;
                } else if (enemy.getSpawnTime() > t1.getSpawnTime()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        readyEnemies.addAll(level.getEnemies());

        map = new GameMap(level.getLevelMap());
        availableRobots = new ArrayList<Class>(Arrays.asList(GemBot.class,
                GunnerBot.class));

        selectRobotPanel = new SelectRobotPanel(availableRobots);
        action = PointerActions.NOTHING;
        selectedRobot = null;

        statusBarField = new Rectangle(0, 0, Configuration.windowWidth, 100);
        gameField = new Rectangle(140, 110, 1000, 500);
        robotBarField = new Rectangle(0, Configuration.windowHeight - 100,
                Configuration.windowWidth, 100);

        bullets = new ArrayList<Bullet>();
        gameState = GameState.PLAY;
        gemsCount = level.getStartGems();
        lives = 3;
        hud = new HudPanel(this.gemsCount, this.lives);
        gems = new ArrayList<Gem>();

        bulletArrayListHelpers = new ArrayListHelpers<Bullet>();
        enemyArrayListHelpers = new ArrayListHelpers<Enemy>();
        robotArrayListHelpers = new ArrayListHelpers<Robot>();
        gemArrayListHelpers = new ArrayListHelpers<Gem>();
    }

    public void update(float delta) {
        gameTime += delta;

        // Create new enemies by spawn time
        if (readyEnemies.size() != 0 && gameTime >= readyEnemies.first()
                .getSpawnTime()) {
            Enemy newEnemy = readyEnemies.pollFirst();
            newEnemy.start();
            map.getEnemies().add(newEnemy);
        }

        // Update enemies state and check for game over
        for (Enemy enemy : map.getEnemies()) {
            enemy.update(delta, map);
            for (Bullet bullet : bullets) {
                if (enemy.getRect().overlaps(bullet.getCollisionRect())) {
                    bullet.damageEnemy(enemy);
                }
            }

            if (enemy.getCollisionRect().x > Configuration.windowWidth - 140) {
                gameState = GameState.GAMEOVER;
            }
        }

        // Update map
        map.updateCells(map.getEnemies());

        // Grab new bullets from game map
        List<Bullet> newBullets = map.grabNewBullets();
        bullets.addAll(newBullets);
        newBullets.clear();

        // Update bullets and apply damage for enemies
        for (Bullet bullet : bullets) {
            bullet.update(delta);
        }

        // Grab new gemsCount
        List<Gem> newGems = map.grabNewGems();
        gems.addAll(newGems);
        newGems.clear();

        // Update gemsCount
        for (Gem gem : gems) {
            gem.update(delta);
        }

        bulletArrayListHelpers.removeIf(bullets, new Predicate<Bullet>() {
            @Override
            public boolean test(Bullet obj) {
                return !obj.isActive();
            }
        });

        gemArrayListHelpers.removeIf(gems, new Predicate<Gem>() {
            @Override
            public boolean test(Gem obj) {
                return !obj.isAlive();
            }
        });

        enemyArrayListHelpers.removeIf(map.getEnemies(), new Predicate<Enemy>() {
            @Override
            public boolean test(Enemy obj) {
                return !obj.isAlive();
            }
        });

        robotArrayListHelpers.removeIf(map.getRobots(), new Predicate<Robot>() {
            @Override
            public boolean test(Robot obj) {
                return !obj.isAlive();
            }
        });

        // Update robots
        for (Robot robot : map.getRobots()) {
            robot.update(delta, map);
        }

        // Update hud
        hud.update(delta, gemsCount, lives);

        // Update robot tiles
        selectRobotPanel.update(delta);

        // Check for win
        if (readyEnemies.size() == 0 && map.getEnemies().size() == 0) {
            gameState = GameState.WIN;
        }

        // JAVA 8 code
//        //Delete death bullets
//        bullets.removeIf(new Predicate<Bullet>() {
//            @Override
//            public boolean test(Bullet bullet) {
//                return !bullet.isActive();
//            }
//        });
//
//        // Delete death enemies
//        map.getEnemies().removeIf(new Predicate<Enemy>() {
//            @Override
//            public boolean test(Enemy enemy) {
//                return !enemy.isAlive();
//            }
//        });
//
//        // Delete death robots
//        map.getRobots().removeIf(new Predicate<Robot>() {
//            @Override
//            public boolean test(Robot robot) {
//                return !robot.isAlive();
//            }
//        });
    }

    public void onClick(int x, int y, int button) {
        switch (action) {
            case NOTHING:
                if (statusBarField.contains(x, y)) {

                } else if (gameField.contains(x, y)) {
                    // Check if gem picked
                    for (Gem gem : gems) {
                        if (gem.contains(x, y)) {
                            this.gemsCount += gem.pickGem();
                        }
                    }
                } else if (robotBarField.contains(x, y)) {
                    Robot newRobot = selectRobotPanel.getRobot(x, y, gemsCount);
                    if (newRobot == null) {
                        return;
                    }
                    action = PointerActions.ADD_ROBOT;
                    selectedRobot = newRobot;
                    selectedRobotTile = selectRobotPanel.getTile(x, y);
                }
                break;
            case ADD_ROBOT:
                // If right mouse button was pressed then cancel robot planting
                if (button == Input.Buttons.RIGHT) {
                    selectedRobot = null;
                    action = PointerActions.NOTHING;
                } else if (button == Input.Buttons.LEFT) {
                    // If button was pressed on game field then plant robot
                    if (gameField.contains(x, y)) {
                        // If cell is empty and we can plant robot - do it
                        // else do nothing
                        if (map.plantRobot(selectedRobot, x, y)) {
                            map.getRobots().add(selectedRobot);
                            int gemsUsed = selectedRobotTile.robotUsed();
                            gemsCount -= gemsUsed;
                            selectedRobot = null;
                            selectedRobotTile = null;
                            action = PointerActions.NOTHING;
                        }
                    }
                }
                break;
            case REMOVE_ROBOT:
                // TODO removing robots
                break;
        }
    }

    public void render(SpriteBatch batcher) {
        hud.render(batcher);
        if (selectedRobot != null) {
            selectedRobot.render(batcher);
        }
        selectRobotPanel.render(batcher);
        for (Robot robot : map.getRobots()) {
            robot.render(batcher);
        }

        for (Enemy enemy : map.getEnemies()) {
            enemy.render(batcher);
        }

        for (Bullet bullet : bullets) {
            bullet.render(batcher);
        }

        for (Gem gem : gems) {
            gem.render(batcher);
        }
    }

    public void onMove(int x, int y) {
        if (action == PointerActions.ADD_ROBOT) {
            selectedRobot.setPosition(x, y);
        }
    }

    public GameState getGameState() {
        return gameState;
    }
}
