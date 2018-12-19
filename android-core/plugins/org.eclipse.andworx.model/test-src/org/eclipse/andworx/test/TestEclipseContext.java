package org.eclipse.andworx.test;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.contexts.RunAndTrack;

public class TestEclipseContext implements IEclipseContext {

	@Override
	public boolean containsKey(String name) {
		return false;
	}

	@Override
	public boolean containsKey(Class<?> clazz) {
		return false;
	}

	@Override
	public Object get(String name) {
		return null;
	}

	@Override
	public <T> T get(Class<T> clazz) {
		return null;
	}

	@Override
	public Object getLocal(String name) {
		return null;
	}

	@Override
	public <T> T getLocal(Class<T> clazz) {
		return null;
	}

	@Override
	public void remove(String name) {

	}

	@Override
	public void remove(Class<?> clazz) {

	}

	@Override
	public void runAndTrack(RunAndTrack runnable) {

	}

	@Override
	public void set(String name, Object value) {

	}

	@Override
	public <T> void set(Class<T> clazz, T value) {

	}

	@Override
	public void modify(String name, Object value) {

	}

	@Override
	public <T> void modify(Class<T> clazz, T value) {

	}

	@Override
	public void declareModifiable(String name) {

	}

	@Override
	public void declareModifiable(Class<?> clazz) {

	}

	@Override
	public void processWaiting() {

	}

	@Override
	public IEclipseContext createChild() {
		return null;
	}

	@Override
	public IEclipseContext createChild(String name) {
		return null;
	}

	@Override
	public IEclipseContext getParent() {
		return null;
	}

	@Override
	public void setParent(IEclipseContext parentContext) {

	}

	@Override
	public void activate() {

	}

	@Override
	public void deactivate() {

	}

	@Override
	public void activateBranch() {

	}

	@Override
	public IEclipseContext getActiveChild() {
		return null;
	}

	@Override
	public IEclipseContext getActiveLeaf() {
		return null;
	}

	@Override
	public void dispose() {

	}

	@Override
	public <T> T getActive(Class<T> clazz) {
		return null;
	}

	@Override
	public Object getActive(String name) {
		return null;
	}

}
