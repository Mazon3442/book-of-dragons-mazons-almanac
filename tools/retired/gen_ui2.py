"""Pixel skin for the Dragon Editor field board. Run after gen_ui.py.

Wear belongs on the wooden frame; parchment and controls stay calm and readable.
"""
from PIL import Image, ImageDraw
import random
import gen_ui as U
from gen_ui import C, CLEAR, px, rect, save

WOOD = [C(h) for h in ('2e201b','4b3024','67432e','865a38','ab7c4b')]
PAPER = [C(h) for h in ('a77a4b','cca06b','e8bd88','f5d5a5','ffe4ba')]
BRASS = [C(h) for h in ('4e341e','79562f','ac7b3d','d7a75c','f1cd83')]
INK = C('3b291e')
RIB = {n:C(h) for n,h in dict(orange='a85527',teal='386e65',green='63723d',purple='765572',blue='49647c').items()}


def rivet(im,x,y):
    rect(im,x,y,x+2,y+2,BRASS[0])
    px(im,[(x,y),(x+1,y)],BRASS[3])
    px(im,[(x,y+1),(x+1,y+1)],BRASS[2])


def satchel():
    """Full-size artwork keeps the chipped outline and grain from repeating at tile boundaries."""
    w,h=284,224
    im=Image.new('RGBA',(w,h),CLEAR)
    d=ImageDraw.Draw(im)
    # Nested edges form a thick backing, with the deepest edge on the unlit side.
    outline=[(6,0),(83,0),(83,1),(200,1),(200,0),(w-8,0),(w-2,5),
             (w-2,76),(w-1,76),(w-1,h-7),(w-7,h-1),(8,h-1),(1,h-7),(1,7)]
    d.polygon(outline,fill=WOOD[0])
    for inset,col in ((2,WOOD[1]),(3,WOOD[3]),(4,WOOD[1]),(5,WOOD[2])):
        d.polygon([(8,inset),(w-10,inset),(w-inset-2,8),(w-inset-2,h-9),
                   (w-10,h-inset-1),(9,h-inset-1),(inset+1,h-9),(inset+1,8)],fill=col)
    rect(im,7,6,w-9,h-8,WOOD[2])
    # Restrained grain: broad adjacent-tone runs, kept out of the header and footer lettering.
    rng=random.Random(38)
    for x,y,span in ((12,7,250),(12,27,250),(12,30,250),(12,219,250)):
        pos=x
        while pos<x+span:
            length=rng.randrange(5,18)
            rect(im,pos,y,min(x+span,pos+length),y,WOOD[rng.choice((1,2,3))])
            pos+=length+rng.randrange(3,9)
    for x in (5,w-7):
        for y in range(17,h-15,19):
            rect(im,x,y,x,y+10,WOOD[1])
            rect(im,x+1,y+2,x+1,y+7,WOOD[3])
    # A leather inset isolates the lettering from the carved wood.
    rect(im,13,9,w-15,24,C('593d2d'))
    rect(im,14,10,w-16,10,C('79543a'))
    rect(im,14,24,w-16,24,WOOD[0])
    rect(im,14,209,w-16,219,C('593d2d'))
    # Paired cut lines, with small breaks like the ink rules in the reference book.
    for x0,x1,y in ((17,96,28),(101,184,28),(189,265,28),(26,79,30),(83,142,30),(171,249,30)):
        rect(im,x0,y,x1,y,WOOD[4] if y==28 else WOOD[1])
    # L-shaped corner plates, with worn bevels and a single rivet each.
    for right in (False,True):
        for bottom in (False,True):
            def point(x,y): return (w-1-x if right else x,h-1-y if bottom else y)
            shape=[point(x,y) for x,y in ((5,4),(20,4),(20,6),(8,6),(8,18),(5,18))]
            d.polygon(shape,fill=BRASS[0])
            for x in range(7,18): im.putpixel(point(x,5),BRASS[3])
            for y in range(6,16): im.putpixel(point(6,y),BRASS[2])
            rx,ry=point(10,10)
            rivet(im,rx-1,ry-1)
    # Small cuts in the outer rim expose warm wood under the stain.
    for x,y in ((37,2),(109,3),(237,2),(2,95),(280,143),(72,221)):
        rect(im,x,y,min(w-1,x+3),y,WOOD[4])
    return im


def parchment(w,h):
    """Uneven paper on a darker under-sheet, with a fine fibre pattern in the flat reading area."""
    im=Image.new('RGBA',(w,h),CLEAR)
    d=ImageDraw.Draw(im)
    d.polygon([(5,2),(w-6,1),(w-1,6),(w-1,h-5),(w-6,h-1),(6,h-1),(1,h-6),(1,7)],fill=C('785036'))
    d.polygon([(4,1),(w-7,1),(w-2,5),(w-3,h-6),(w-8,h-2),(5,h-3),(0,h-7),(0,5)],fill=PAPER[0])
    outline=[(5,0),(w//4,0),(w//4,1),(w//2-7,1),(w//2-7,0),(w-9,0),(w-4,3),
             (w-3,13),(w-4,16),(w-2,18),(w-3,h//2),(w-4,h//2+2),(w-3,h//2+5),
             (w-4,h-11),(w-8,h-5),(w//2+12,h-5),(w//2+9,h-3),
             (w//3,h-4),(w//3-3,h-6),(8,h-5),(3,h-9),(2,23),(3,20),(1,17),(2,5)]
    d.polygon(outline,fill=PAPER[1])
    # Successive inset surfaces put darker, visibly layered edges under the pale face.
    d.polygon([(6,2),(w-10,2),(w-6,5),(w-6,h-12),(w-10,h-7),(9,h-7),(5,h-11),(4,7)],fill=PAPER[2])
    d.polygon([(7,3),(w-11,3),(w-7,6),(w-7,h-13),(w-11,h-8),(9,h-8),(6,h-12),(5,8)],fill=PAPER[3])
    rect(im,8,3,w-12,3,PAPER[4])
    rect(im,5,9,5,h-14,PAPER[4])
    # Low-contrast cream fibres are deliberate pairs, never random dark speckles.
    for y in range(8,h-12,6):
        for x in range(8+(y//6%2)*3,w-10,8):
            px(im,[(x,y)],C('f9dcb3'))
            if (x+2*y)%5==0: px(im,[(x+1,y)],C('f7d9ad'))
    # Short worn corner creases and the lifted lower-right corner stay outside control padding.
    for x,y in ((7,7),(8,h-10),(w-10,8)):
        px(im,[(x,y),(x+1,y),(x,y+1)],PAPER[0])
        px(im,[(x+1,y+1),(x+2,y+1)],PAPER[2])
    d.polygon([(w-12,h-8),(w-7,h-14),(w-7,h-8)],fill=PAPER[0])
    d.polygon([(w-11,h-9),(w-7,h-13),(w-7,h-9)],fill=PAPER[4])
    return im


def sheet():
    return parchment(264,174)


def card():
    w,h=88,104
    im=parchment(w,h)
    # Drafting marks make this read as a pinned dragon study rather than an empty UI rectangle.
    for x,y,dx,dy in ((8,12,1,1),(w-11,12,-1,1),(8,h-16,1,-1),(w-11,h-16,-1,-1)):
        for i in range(5):
            px(im,[(x+i*dx,y),(x,y+i*dy)],PAPER[0])
    for x in (14,22,31,43,54,65):
        px(im,[(x,h-11),(x+2,h-11)],PAPER[1])
    return im


def pin():
    im=Image.new('RGBA',(7,8),CLEAR)
    rect(im,1,0,5,5,BRASS[0])
    rect(im,2,1,4,4,BRASS[2])
    rect(im,2,1,4,1,BRASS[4])
    rect(im,2,2,2,4,BRASS[3])
    px(im,[(3,5),(3,6)],BRASS[0])
    return im


def crest():
    im=Image.new('RGBA',(13,13),CLEAR)
    rows=['..#.......#..','..##.....##..','.####...####.','##.##...##.##','...#######...','..###.#.###..','...#######...','....#####....','.....###.....']
    for y,row in enumerate(rows):
        for x,c in enumerate(row):
            if c=='#': im.putpixel((x,y+2),BRASS[3] if y<4 else BRASS[2])
    return im


def ribbon_sprite(color,selected):
    w,h=(36 if selected else 30),24
    im=Image.new('RGBA',(w,h),CLEAR)
    ImageDraw.Draw(im).polygon([(2,0),(w-1,0),(w-1,h-1),(2,h-1),(0,h-3),(0,2)],fill=WOOD[0])
    rect(im,2,1,w-1,h-3,PAPER[3] if selected else PAPER[2])
    rect(im,2,1,w-1,1,PAPER[4] if selected else PAPER[3])
    rect(im,3,h-4,w-9,h-4,PAPER[0])
    for x,y in ((5,2),(12,3),(20,h-5)):
        px(im,[(x,y),(x+1,y)],PAPER[4])
    rect(im,2,3,3,h-5,RIB[color])
    rect(im,w-8,2,w-1,h-3,WOOD[1])
    if selected: rect(im,1,h-3,w-9,h-2,RIB[color])
    return im


ICON_ART = {
    'bond':['..##...##..','.####.####.','###########','###########','.#########.','..#######..','...#####...','....###....','.....#.....'],
    'vitals':['....###....','....###....','....###....','###########','###########','###########','....###....','....###....','....###....'],
    'mind':['..###.###..','.##.###.##.','.#.##.##.#.','.##.#.#.##.','..#######..','....###....','.....#.....'],
    'variants':['....##.....','...####....','..######...','.########..','##########.','.########..','..######...','...####....'],
    'social':['..##...##..','.####.####.','..##...##..','...........','.####.####.','###########','###########'],
}


def tab_icon(name):
    im=Image.new('RGBA',(13,13),CLEAR)
    rows=ICON_ART[name]
    for y,row in enumerate(rows):
        for x,c in enumerate(row):
            if c=='#':
                yy=y+(13-len(rows))//2
                im.putpixel((x+1,yy),INK)
                if y>0 and rows[y-1][x]=='#' and x>0 and row[x-1]=='#':
                    color={'bond':'orange','vitals':'teal','mind':'green','variants':'purple','social':'blue'}[name]
                    im.putpixel((x+1,yy),RIB[color])
    return im


def button(kind):
    im=Image.new('RGBA',(24,20),CLEAR)
    body,hi,low={'normal':('67432e','94633e','4b3024'),'hover':('7d4a2c','bf8444','51311f'),'disabled':('6c5948','8b755c','554635')}[kind]
    U.rounded(im,0,0,23,19,WOOD[0])
    rect(im,1,1,22,17,C(body))
    rect(im,2,1,21,1,C(hi))
    rect(im,1,2,1,16,C(hi))
    rect(im,2,18,22,18,C(low))
    rect(im,3,3,20,3,C(low))
    rect(im,3,16,20,16,C(hi))
    px(im,[(2,3),(2,4),(21,15),(21,16)],BRASS[2] if kind!='disabled' else C(hi))
    if kind=='hover': rect(im,2,2,21,2,BRASS[3])
    return im


def slider_track():
    im=Image.new('RGBA',(16,8),CLEAR)
    rect(im,0,1,15,6,PAPER[0])
    rect(im,1,2,14,5,WOOD[1])
    rect(im,1,6,14,6,PAPER[4])
    return im


def slider_handle():
    im=Image.new('RGBA',(8,12),CLEAR)
    U.rounded(im,0,0,7,11,BRASS[0])
    rect(im,1,1,6,10,BRASS[2])
    rect(im,1,1,5,1,BRASS[4])
    rect(im,1,2,1,9,BRASS[3])
    rect(im,3,3,3,8,BRASS[0])
    return im


# Existing software previews call these factories on gen_ui.
U.button,U.slider_track,U.slider_handle=button,slider_track,slider_handle


if __name__=='__main__':
    save('satchel',satchel())
    save('sheet',sheet())
    save('card',card())
    save('pin',pin())
    save('editor_crest',crest())
    for c in RIB:
        save('tab_'+c,ribbon_sprite(c,False))
        save('tab_'+c+'_selected',ribbon_sprite(c,True))
    for n in ICON_ART: save('icon_'+n,tab_icon(n))
    for k in ('normal','hover','disabled'): save('button_'+k,button(k),(24,20,3))
    save('slider_track',slider_track(),(16,8,3))
    save('slider_handle',slider_handle())
    print('Field-board skin written')
