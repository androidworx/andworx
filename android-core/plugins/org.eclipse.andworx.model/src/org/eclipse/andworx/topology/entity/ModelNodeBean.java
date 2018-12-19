package org.eclipse.andworx.topology.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.eclipse.andworx.record.ModelType;

/**
 * ModelNodeEntity
 * Persistent anchor for a component of a graph. 
 * Each node has a model which identifies what the node contains and is mapped by ordinal to an enumeration constant.
 * The top node of a graph has a special model called "root".
 * Graph parent and child relationships are expressed as OneToOne and OneToMany respectively.
 */
@Entity(name = "tableNodes")
public class ModelNodeBean  implements Serializable {
    
	private static final long serialVersionUID = -904697273203162010L;

    /** Column name in join table for model type foreign key */
	public final static String MODEL_ID_FIELD_NAME = "model_id";

	@Id @GeneratedValue
    int _id;
    @OneToMany(mappedBy="node_id", fetch=FetchType.EAGER)
    Collection<ModelTypeBean> modelTypeBeans;
    @Column(nullable = false)
    String name;
    @Column(nullable = false)
    String title;
    @Column(nullable = false)
    int level;
    // On OneToMany association, the "Many" side is linked using a "join table" annotation. 
    // The association is only record-to-record, not the usual table-to-table. As a consequence,
    // the entity table cannot be created from the class and must be provided prior using SQL.
    //@Column(nullable = false)
    //int _parent_id;
    @OneToOne
    @JoinColumn(name="_parent_id", referencedColumnName="_id")
    ModelNodeBean parent;
    @OneToMany(mappedBy="_parent_id", fetch=FetchType.EAGER)
    // With foreign collections, OrmLite always uses primary id on the "one" side
    // and on the "many" side, defaults to to first field with type matching collection generic type.
    // If "mappedby" is specifed, this overrides the default, but the field type must still match.
    Collection<ModelNodeBean> _children;

 
    ModelNodeBean() {
    }
    
    public ModelNodeBean(String name, String title, ModelNodeBean parent) {
    	this.name = name;
    	this.title = title;
    	setParent(parent);
    }
    
    /**
     * Returns primary key
     * @return int
     */
    public int get_id()
    {
        return _id;
    }
    /**
     * Set primary key
     * @param _id int
     */
    public void set_id(int _id)
    {
        this._id = _id;
    }
    
    /**
     * Returns model ordinal value
     * @return int
     */
    public List<ModelType>  getModelTypes() 
    {
    	List<ModelType> modelTypes = null;
    	if (modelTypeBeans != null) {
        	modelTypes = new ArrayList<>();
     		Iterator<ModelTypeBean> iterator = modelTypeBeans.iterator();
     		while (iterator.hasNext())
     			modelTypes.add(iterator.next().getModelType());
    	}
        return modelTypes;
    }

    public Collection<ModelTypeBean> getModelTypeBeans() {
    	return modelTypeBeans;
    }
    
    /**
     * Returns node name
     * @return String
     */
    public String getName() 
    {
        return name;
    }
    
    /**
     * Set node name (computer friendly)
     * @param name String
     */
    public void setName(String name) 
    {
        this.name = name;
    }
    
    /**
     * Returns node title (human readable)
     * @return String
     */
    public String getTitle() 
    {
        return title;
    }
    
    /**
     * Set node title (human readable)
     * @param title String
     */
    public void setTitle(String title) 
    {
        this.title = title;
    }
    
    /**
     * Returns depth in graph, starting at 1 for the solitary root node
     * @return int
     */
    public int getLevel() 
    {
        return level;
    }
    
    /**
     * Sets depth in graph, starting at 1 for the solitary root node
     * @param level int
     */
    public void setLevel(int level) 
    {
        this.level = level;
    }
    
    /**
     * Returns parent node or, if root node, self
     * @return ModelNodeEntity
     */
    public ModelNodeBean getParent() 
    {
        return parent;
    }
    
    /**
     * Sets parent node
     * @param parent ModelNodeEntity
     */
    public void setParent(ModelNodeBean parent) 
    {
    	if (parent == null) {
    		// Root node?
    		return;
    	}
        this.parent = parent;
        /*
        if (parent._children == null) {
        	// Create a transient children list
        	// This will be replaced by a ForeignCollection on first time the parent is returned in a query
        	parent._children = new ArrayList<ModelNodeBean>();
        	if (parent == this) // Special case for root node
        		return;
        }
        */
    }
    
    /**
     * Returns child nodes
     * @return Collection&lt;ModelNodeEntity&gt;
     */
    public Collection<ModelNodeBean> get_children() 
    {
        return _children;
    }
    
    /**
     * Sets child nodes
     * @param _children Collection&lt;ModelNodeEntity&gt;
     */
    public void set_children(Collection<ModelNodeBean> _children) 
    {
        this._children = _children;
    }
    
    /**
     * Returns parent primary key
     * @return int
     */
    public int get_parent_id()
    {
        return parent != null ? parent.get_id() : 0;
    }

	public ModelTypeBean getModelTypeBean(ModelType modelType) {
		if (modelTypeBeans != null) {
			Iterator<ModelTypeBean> iterator = modelTypeBeans.iterator();
			while (iterator.hasNext()) {
				ModelTypeBean modelTypeBean = iterator.next();
				if (modelTypeBean.getModelType() == modelType)
					return modelTypeBean;
			}
		}
		return null;
	}
    
 }
