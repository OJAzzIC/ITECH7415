// DEPRECATED: prefer a [[child_profile]] entry in simulation.conf, e.g.
//   [[child_profile]]
//   name = "inattentive"
//   count = 1
//   attentiveness = 0.5
// which uses the standard child.asl with launcher-injected beliefs.
// This file remains only for manually adding an inattentive child via the
// .jcm file.  It reuses all of child.asl's plans; the beliefs below
// override the attentiveness defaults.
{ include("child.asl") }
attentiveness_heard(0.5).
attentiveness_seen(0.5).
