# RTS Game - TODO List

## Core Gameplay Features

### Resource System
- [ ] Implement resource gathering mechanics
  - [ ] Units can target and gather from gold, stone, berries, trees
  - [ ] Units automatically return resources to nearest town center or specific building such as wood cutter, mine camp, mill
  - [ ] Display resource carry amount on units
  - [ ] Deplete resources when gathered (resource depletion system)
- [ ] Add resource drop-off points
  - [ ] Town Center accepts all resources
  - [ ] Specialized buildings (lumber camp, mining camp, mill)
- [ ] Resource generation/income system (if applicable)

### Unit Production
- [ ] Complete unit production system
  - [x] Production queue UI (basic)
  - [ ] Multiple unit types (villager, scout, infantry, cavalry, archers)
  - [ ] Production costs (resources required)
  - [ ] Production time/progress bar
  - [ ] Cancel production functionality
  - [ ] Queue management (remove items, reorder)
- [ ] Unit spawning at rally points
  - [x] Basic rally point setting
  - [ ] Units spawn and auto-move to rally point
  - [ ] Visual indicator for rally point

### Building System
- [ ] Building construction
  - [ ] Villagers can place buildings
  - [ ] Building placement validation (no overlap, terrain requirements)
  - [ ] Construction progress/health bar
  - [ ] Multiple building types (barracks, archery range, stable, etc.)
  - [ ] Building costs and requirements
- [ ] Building functionality
  - [ ] Different buildings produce different units
  - [ ] Tech research buildings (blacksmith, university, monastery)
  - [ ] Defensive structures (walls, towers, gates)
- [ ] Building unique IDs
  - [ ] Replace building array index with proper DB IDs
  - [ ] Update Building model with ID field
  - [ ] Update all building-related endpoints to use real IDs

### Combat System
- [ ] Unit combat mechanics
  - [ ] Attack command (right-click enemy units)
  - [ ] Attack animations
  - [ ] Damage calculation (attack, armor, bonuses)
  - [ ] Unit death and removal
  - [ ] Experience/veterancy system (optional)
- [ ] Unit types and counters
  - [ ] Rock-paper-scissors balance (infantry > archers > cavalry > infantry)
  - [ ] Unique unit abilities
- [ ] Building combat
  - [ ] Buildings can be attacked
  - [ ] Building destruction
  - [ ] Garrison units in buildings
  - [ ] Arrow fire from defensive buildings

### AI & Pathfinding
- [ ] Improved pathfinding
  - [ ] A* or similar algorithm for unit movement
  - [ ] Avoid obstacles (buildings, trees, other units)
  - [ ] Formation movement for groups
  - [ ] Unit collision detection
- [ ] Basic AI opponent
  - [ ] AI villager economy
  - [ ] AI military production
  - [ ] AI attack waves
  - [ ] Difficulty levels

## UI/UX Improvements

### Game Interface
- [ ] Minimap improvements
  - [ ] Click to move camera
  - [ ] Show units as dots
  - [ ] Show explored/unexplored areas (fog of war)
- [ ] Better unit info panel
  - [ ] Unit stats (HP, attack, armor, speed)
  - [ ] Unit upgrades indicator
  - [ ] Unit experience/level
- [ ] Building info panel improvements
  - [ ] Show building health
  - [ ] Show garrisoned units
  - [ ] Show research progress
- [ ] Command panel
  - [ ] Context-sensitive commands based on selection
  - [ ] Formation controls
  - [ ] Stance controls (aggressive, defensive, stand ground)

### Controls & Hotkeys
- [x] Double-click to select all units of same type
- [ ] Control groups (Ctrl+1-9 to create, 1-9 to select)
- [ ] Camera hotkeys (arrow keys, WASD, or screen edge scrolling)
- [ ] Idle villager hotkey (period key)
- [ ] Go to last notification/alert
- [ ] Cycle through town centers
- [ ] Attack-move command (A + click)
- [ ] Patrol command (Z + click)

### Visual Feedback
- [ ] Health bars above units/buildings
- [ ] Attack animations and projectiles
- [ ] Building construction animation
- [ ] Resource gathering animation
- [ ] Death animations
- [ ] Selection feedback improvements (better circles/highlights)
- [ ] Damage numbers/indicators

## Multiplayer & Networking

### Game Session Management
- [x] Basic lobby system
- [x] Game start synchronization
- [ ] Player disconnect handling improvements
- [ ] Reconnection support
- [ ] Spectator mode
- [ ] Game pause/resume (multiplayer vote)
- [ ] Surrender/resign functionality

### Performance & Optimization
- [ ] Optimize WebSocket message size
- [ ] Client-side prediction for unit movement
- [ ] Lag compensation
- [ ] Server-authoritative validation
- [ ] Cheat prevention

## Map & Terrain

### Map Generation
- [x] RMS (Random Map Script) system
- [ ] More map templates (Arabia, Black Forest, Arena, etc.)
- [ ] Water maps and naval units
- [ ] Cliffs/elevation system
- [ ] Map size options (tiny, small, medium, large, huge)

### Map Features
- [ ] Fog of war
  - [ ] Unexplored areas (black)
  - [ ] Explored but not visible (gray/shadowed)
  - [ ] Visible areas (full color)
- [ ] Line of sight calculations
- [ ] Destructible terrain (trees disappear when chopped)
- [ ] Map revealer cheat code (for debugging)

## Technologies & Upgrades

### Tech Tree
- [ ] Age advancement system (Dark Age → Feudal → Castle → Imperial)
- [ ] Economic upgrades (gathering rate, carry capacity)
- [ ] Military upgrades (attack, armor, HP)
- [ ] Unique technologies per civilization
- [ ] Tech dependencies and prerequisites

### Civilizations
- [ ] Multiple civilizations with unique bonuses
- [ ] Unique units per civilization
- [ ] Unique buildings per civilization
- [ ] Civilization selection in lobby

## Game Modes & Win Conditions

### Victory Conditions
- [ ] Conquest (destroy all enemy buildings/units)
- [ ] Wonder victory (build and defend wonder for X minutes)
- [ ] Relic victory (control majority of relics for X minutes)
- [ ] Score victory (highest score after time limit)
- [ ] Team victory (allied win conditions)

### Game Modes
- [ ] Standard random map
- [ ] Death match (start with resources)
- [ ] Regicide (protect king unit)
- [ ] King of the Hill
- [ ] Capture the relic
- [ ] Custom scenarios

## Polish & Quality of Life

### Sound & Music
- [ ] Unit selection sounds
- [ ] Building construction sounds
- [ ] Combat sounds (sword clashes, arrows, etc.)
- [ ] Resource gathering sounds
- [ ] Background music
- [ ] Ambient sounds (birds, water, etc.)
- [ ] Town bell alert sound

### Game Settings
- [ ] Graphics quality settings
- [ ] Sound volume controls
- [ ] Game speed adjustment
- [ ] Color blind mode
- [ ] Keybinding customization
- [ ] Auto-save functionality

### Statistics & Replay
- [ ] Post-game statistics
  - [ ] Resources gathered
  - [ ] Units created/lost
  - [ ] Buildings constructed/destroyed
  - [ ] Score over time graph
- [ ] Replay system
  - [ ] Save game state at intervals
  - [ ] Playback controls (play, pause, speed)
  - [ ] Camera controls during replay

## Technical Debt & Refactoring

### Code Quality
- [x] Refactor client-side to SOLID principles
- [x] Refactor server-side with DTOs
- [ ] Add comprehensive unit tests
  - [ ] Backend service tests
  - [ ] Frontend component tests
  - [ ] Integration tests
- [ ] Add API documentation (Swagger/OpenAPI)
- [ ] Improve error handling and logging
- [ ] Code coverage reports

### Database & Persistence
- [ ] Persist game state to database
- [ ] Save/load game functionality
- [ ] Player profiles and statistics
- [ ] Match history
- [ ] Leaderboard system
- [ ] Achievement system

### Security
- [ ] Input validation on all endpoints
- [ ] Rate limiting to prevent spam
- [ ] Authentication & authorization
- [ ] Secure WebSocket connections (WSS)
- [ ] Anti-cheat measures

## Documentation

### Developer Documentation
- [x] Architecture overview (partially in .clinerules)
- [ ] API documentation
- [ ] Database schema documentation
- [ ] Setup/installation guide
- [ ] Contribution guidelines
- [ ] Code style guide

### Player Documentation
- [ ] Game manual/tutorial
- [ ] Hotkey reference
- [ ] Tech tree visualization
- [ ] Civilization bonuses guide
- [ ] Strategy guides

## Future Enhancements

### Advanced Features
- [ ] Campaign mode with missions
- [ ] Map editor
- [ ] Scenario editor
- [ ] Mod support
- [ ] Custom maps upload/download
- [ ] In-game chat (all, team, allies)
- [ ] Taunts/emotes system
- [ ] Tournament mode with brackets

### Platform Support
- [ ] Mobile-friendly interface
- [ ] Touch controls for tablets
- [ ] Desktop app (Electron wrapper)
- [ ] Steam integration (if applicable)

---

## Priority Rankings

### P0 - Critical (MVP)
- Resource gathering mechanics
- Unit production system (villager, basic military)
- Building construction (town center, barracks)
- Basic combat system
- Fog of war
- Win condition (conquest)

### P1 - High Priority
- Multiple unit types
- Tech tree and age advancement
- Building unique IDs fix
- Control groups
- Improved pathfinding
- Health bars and visual feedback

### P2 - Medium Priority
- AI opponent
- More civilizations
- Replay system
- Sound effects
- Game statistics
- Tech research

### P3 - Nice to Have
- Campaign mode
- Map editor
- Advanced game modes
- Mobile support
- Mod support
- Tournament features

---

**Last Updated:** 2025-12-29
