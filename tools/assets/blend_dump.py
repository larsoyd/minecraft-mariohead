"""Runs inside Blender: evaluate the head at one subdivision level and dump every face corner.

Usage: blender --background HEAD.blend --python blend_dump.py -- OUT.json LEVEL
"""

import json
import sys

import bpy

out_path, level = sys.argv[-2], int(sys.argv[-1])


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def add_closed_face():
    """A copy of the face cage with the lower lip raised in world space before subdivision."""
    face = bpy.data.objects["mario_face"]
    closed = face.copy()
    closed.data = face.data.copy()
    closed.name = "mario_face_closed"
    bpy.context.collection.objects.link(closed)
    inverse = closed.matrix_world.inverted()
    for vertex in closed.data.vertices:
        p = closed.matrix_world @ vertex.co
        weight = smooth((0.65 - abs(p.x)) / 0.35) * smooth((0.05 - p.y) / 0.53)
        p.z += max(0.0, 0.49 - p.z) * 0.96 * weight
        vertex.co = inverse @ p


def palette_slots(mesh):
    """Give every swatch cell its own material slot so subdivision cannot blend palette colours."""
    uv = mesh.uv_layers.active.data
    material = mesh.materials[0]
    slots, cells = {}, []
    for poly in mesh.polygons:
        n = len(poly.loop_indices)
        centre = tuple(round(sum(uv[i].uv[k] for i in poly.loop_indices) / n, 5) for k in range(2))
        if centre not in slots:
            slots[centre] = len(slots)
            cells.append(centre)
            if len(slots) > 1:
                mesh.materials.append(material)
        poly.material_index = slots[centre]
    return cells


def evaluate(ob):
    copy = ob.copy()
    copy.data = ob.data.copy()
    bpy.context.collection.objects.link(copy)
    material = copy.data.materials[0].name if copy.data.materials else None
    cells = palette_slots(copy.data) if material == "mario_head_swatch" else None
    bpy.context.view_layer.objects.active = copy
    for mod in list(copy.modifiers):
        if mod.type == "SUBSURF":
            if level <= 0:
                copy.modifiers.remove(mod)
                continue
            mod.levels = mod.render_levels = level
        bpy.ops.object.modifier_apply(modifier=mod.name)
    me = copy.data
    mw = copy.matrix_world
    nw = mw.inverted().transposed().to_3x3()
    uv = me.uv_layers.active.data if me.uv_layers.active else None
    data = {
        "material": material,
        "positions": [list(mw @ v.co) for v in me.vertices],
        "polys": [],
    }
    for poly in me.polygons:
        corners = []
        for li in poly.loop_indices:
            loop = me.loops[li]
            normal = (nw @ me.corner_normals[li].vector).normalized()
            corners.append({
                "v": loop.vertex_index,
                "n": list(normal),
                "uv": list(uv[li].uv) if uv else [0.0, 0.0],
            })
        cell = cells[poly.material_index] if cells else None
        data["polys"].append({"corners": corners, "cell": cell})
    bpy.data.objects.remove(copy, do_unlink=True)
    return data


add_closed_face()
dump = {ob.name: evaluate(ob) for ob in list(bpy.data.objects) if ob.type == "MESH"}
with open(out_path, "w") as f:
    json.dump(dump, f)
print("DUMPED", level, sum(len(d["polys"]) for d in dump.values()))
