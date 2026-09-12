// modifications made for aubio:
//  - replace all 'double' with 'smpl_t'
//  - include "aubio_priv.h" (for config.h and types.h)
//  - add missing prototypes
//  - use COS, SIN, and ATAN macros
//  - add cast to (smpl_t) to avoid float conversion warnings
//  - declare initialization as static
//  - prefix public function with aubio_ooura_

#include "aubio_priv.h"

void aubio_ooura_cdft(int n, int isgn, smpl_t *a, int *ip, smpl_t *w);
void aubio_ooura_rdft(int n, int isgn, smpl_t *a, int *ip, smpl_t *w);
void aubio_ooura_ddct(int n, int isgn, smpl_t *a, int *ip, smpl_t *w);
void aubio_ooura_ddst(int n, int isgn, smpl_t *a, int *ip, smpl_t *w);
void aubio_ooura_dfct(int n, smpl_t *a, smpl_t *t, int *ip, smpl_t *w);
void aubio_ooura_dfst(int n, smpl_t *a, smpl_t *t, int *ip, smpl_t *w);
static void makewt(int nw, int *ip, smpl_t *w);
static void makect(int nc, int *ip, smpl_t *c);
static void bitrv2(int n, int *ip, smpl_t *a);
static void bitrv2conj(int n, int *ip, smpl_t *a);
static void cftfsub(int n, smpl_t *a, smpl_t *w);
static void cftbsub(int n, smpl_t *a, smpl_t *w);
static void cft1st(int n, smpl_t *a, smpl_t *w);
static void cftmdl(int n, int l, smpl_t *a, smpl_t *w);
static void rftfsub(int n, smpl_t *a, int nc, smpl_t *c);
static void rftbsub(int n, smpl_t *a, int nc, smpl_t *c);
static void dctsub(int n, smpl_t *a, int nc, smpl_t *c);
static void dstsub(int n, smpl_t *a, int nc, smpl_t *c);
