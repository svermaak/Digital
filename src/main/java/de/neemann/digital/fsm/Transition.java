/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.fsm;

import de.neemann.digital.analyse.expression.Expression;
import de.neemann.digital.analyse.parser.ParseException;
import de.neemann.digital.analyse.parser.Parser;
import de.neemann.digital.draw.graphics.*;
import de.neemann.digital.lang.Lang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transition
 */
public class Transition extends Movable<Transition> {
    private static final float EXPANSION_TRANS = 0.001f;

    private final State fromState;
    private final State toState;
    private String condition;
    private transient Expression conditionExpression;


    /**
     * Creates a new transition
     *
     * @param fromState the state to leave
     * @param toState   the state to enter
     * @param condition the condition
     */
    public Transition(State fromState, State toState, String condition) {
        super();
        this.fromState = fromState;
        this.toState = toState;
        this.condition = condition == null ? "" : condition;
        initPos();
    }

    /**
     * Calculates the repulsive forces
     *
     * @param states      the states
     * @param transitions the transitions
     */
    void calcForce(List<State> states, List<Transition> transitions) {
        float preferredDist = Math.max(fromState.getVisualRadius(), toState.getVisualRadius()) * 5;
        calcForce(preferredDist, states, transitions);
    }

    /**
     * Calculates the repulsive forces
     *
     * @param preferredDist the preferred distance
     * @param states        the states
     * @param transitions   the transitions
     */
    void calcForce(float preferredDist, List<State> states, List<Transition> transitions) {

        if (fromState != toState) {
            VectorFloat dir = fromState.getPos().sub(toState.getPos());
            float len = dir.len();
            float d = len - preferredDist;
            dir = dir.mul(EXPANSION_TRANS * d);
            toState.addToForce(dir);
            fromState.addToForce(dir.mul(-1));
        }

        resetForce();
        VectorFloat center = fromState.getPos().add(toState.getPos()).mul(0.5f);
        addAttractiveTo(center, 1);

        if (!isInitialTransition()) {
            for (State s : states)
                if (s != fromState && s != toState)
                    addRepulsive(s.getPos(), 2000);

            for (Transition t : transitions)
                if (t != this)
                    addRepulsive(t.getPos(), 1500);
        }
    }

    private boolean isInitialTransition() {
        return getFsm() != null && getFsm().isInitial(this);
    }

    @Override
    public void setPos(VectorFloat position) {
        if (fromState != toState) {
            VectorFloat dist = toState.getPos().sub(fromState.getPos());
            if (dist.getXFloat() != 0 || dist.getYFloat() != 0) {
                dist = dist.norm();
                VectorFloat start = fromState.getPos().add(dist.mul(fromState.getVisualRadius()));
                VectorFloat end = toState.getPos().sub(dist.mul(toState.getVisualRadius()));

                VectorFloat p = position.sub(start);
                VectorFloat n = dist.getOrthogonal();
                float l = p.mul(n);
                super.setPos(start.add(end).div(2).add(n.mul(l)));
                return;
            }
        }
        super.setPos(position);
    }

    /**
     * Draws the transition
     *
     * @param gr the Graphic instance to draw to
     */
    public void drawTo(Graphic gr) {
        final VectorFloat start;
        final VectorFloat anchor;
        final VectorFloat end;
        if (fromState == toState) {
            VectorFloat dif = getPos().sub(fromState.getPos()).getOrthogonal().mul(0.5f);
            VectorFloat ps = getPos().add(dif);
            VectorFloat pe = getPos().sub(dif);
            start = fromState.getPos().add(
                    ps.sub(fromState.getPos()).norm().mul(fromState.getVisualRadius() + Style.MAXLINETHICK));
            end = toState.getPos().add(
                    pe.sub(toState.getPos()).norm().mul(toState.getVisualRadius() + Style.MAXLINETHICK + 2));
        } else {
            start = fromState.getPos().add(
                    getPos().sub(fromState.getPos()).norm().mul(fromState.getVisualRadius() + Style.MAXLINETHICK));
            end = toState.getPos().add(
                    getPos().sub(toState.getPos()).norm().mul(toState.getVisualRadius() + Style.MAXLINETHICK + 2));
        }

        final Style arrowStyle = Style.SHAPE_PIN;
        // arrow line
        anchor = getPos().mul(2).sub(start.div(2)).sub(end.div(2));
        gr.drawPolygon(new Polygon(false).add(start).add(anchor, end), arrowStyle);

        // arrowhead
        VectorFloat dir = anchor.sub(end).norm().mul(20);
        VectorFloat lot = dir.getOrthogonal().mul(0.3f);
        gr.drawPolygon(new Polygon(false)
                .add(end.add(dir).add(lot))
                .add(end.sub(dir.mul(0.1f)))
                .add(end.add(dir).sub(lot)), arrowStyle);

        // text
        VectorFloat textPos = getPos();
        final int fontSize = Style.NORMAL.getFontSize();
        if (fromState.getPos().getYFloat() < getPos().getYFloat()
                && toState.getPos().getYFloat() < getPos().getYFloat())
            textPos = textPos.add(new VectorFloat(0, fontSize / 2f));
        if (fromState.getPos().getYFloat() > getPos().getYFloat()
                && toState.getPos().getYFloat() > getPos().getYFloat())
            textPos = textPos.add(new VectorFloat(0, -fontSize / 2f));

        if (condition != null && condition.length() > 0) {
            gr.drawText(textPos, textPos.add(new Vector(1, 0)), condition, Orientation.CENTERCENTER, Style.INOUT);
        }
        if (getValues() != null && getValues().length() > 0) {
            textPos = textPos.add(new VectorFloat(0, fontSize));
            gr.drawText(textPos, textPos.add(new Vector(1, 0)), Lang.get("fsm_set_N", getValues()), Orientation.CENTERCENTER, Style.INOUT);
        }
    }

    /**
     * Initializes the position of the transition
     */
    void initPos() {
        setPos(fromState.getPos().add(toState.getPos()).mul(0.5f)
                .add(new VectorFloat((float) Math.random() - 0.5f, (float) Math.random() - 0.5f).mul(2)));
    }

    /**
     * Sets the condition
     *
     * @param condition the condition
     */
    public void setCondition(String condition) {
        if (!this.condition.equals(condition)) {
            this.condition = condition;
            wasModified();
            conditionExpression = null;
            if (getFsm() != null)
                getFsm().resetInitInitialization();
        }
    }

    /**
     * @return returns the condition
     */
    public String getCondition() {
        return condition;
    }

    /**
     * @return the condition
     * @throws FiniteStateMachineException FiniteStateMachineException
     */
    Expression getConditionExpression() throws FiniteStateMachineException {
        if (conditionExpression == null) {
            if (condition != null && condition.trim().length() > 0)
                try {
                    ArrayList<Expression> ex = new Parser(condition).parse();
                    if (ex.size() != 1)
                        throw new FiniteStateMachineException(Lang.get("err_fsmErrorInCondition_N", condition));

                    this.conditionExpression = ex.get(0);
                } catch (IOException | ParseException e) {
                    throw new FiniteStateMachineException(Lang.get("err_fsmErrorInCondition_N", condition), e);
                }
        }
        return conditionExpression;
    }

    /**
     * @return true if this transition has a condition
     * @throws FiniteStateMachineException FiniteStateMachineException
     */
    boolean hasCondition() throws FiniteStateMachineException {
        return getConditionExpression() != null;
    }

    /**
     * @return the starting state
     */
    State getStartState() {
        return fromState;
    }

    /**
     * @return the target state
     */
    State getTargetState() {
        return toState;
    }

    /**
     * Gives true if the position matches the transition.
     *
     * @param pos the position
     * @return true if pos matches the transition
     */
    public boolean matches(Vector pos) {
        return pos.sub(getPos()).len() < 50;
    }

    @Override
    public String toString() {
        return fromState + " --[" + condition + "]-> " + toState;
    }

}
