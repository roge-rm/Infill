# Drawing buildings

How every building in gen_tiles.py should look, so the town reads as one place.

- Light from the north-west, shadows to the south-east. Anything that stands up adds a caster.
- A one-pixel dark outline round every wall and roof.
- Walls show what they're made of: brick courses, boards, stone bands, curtain wall with
  mullions, and windows with sills in the style of their era.
- No bare roofs. Flat roofs go through `flat_roof`, which picks a finish to suit the building,
  lays the felt, lights the parapet and adds plant for the roof's size. Pass `busy=False` where
  the roof is already full, such as under a helipad.
- No bare lots. Homes get a yard (`front_yard`), works a loading apron, offices and civic
  buildings paving or lawn, and parks smooth paths (`path_line` curves through its points).
- Versions differ in shape, not just colour: a different roof, a set-back storey (`setback`),
  a corner turret (`turret`), a light well (`light_well`), a roof sign (`roof_sign`), a different
  layout of the yard or what stands in it.
- Something that says what it is at a glance: a sign, an emblem, its vehicles, its goods.
- Era palettes: warm brick, stone, slate and shingle to the 1930s; pale brick and tar to the
  1960s; concrete and ribbon windows to the 1980s; glass, timber, pale roofs, solar and green
  roofs after.
- Check a new drawing in every look, snow too, and beside the others on a sheet before it goes in.
